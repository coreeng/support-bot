package com.coreeng.supportbot.docanalysis;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;

import com.coreeng.supportbot.analysis.AnalysisPrompt;
import com.coreeng.supportbot.analysis.AnalysisPromptLoadException;
import com.coreeng.supportbot.analysis.AnalysisPromptType;
import com.coreeng.supportbot.analysis.AnalysisService;
import com.coreeng.supportbot.config.ConditionalOnLlmEnabled;
import com.coreeng.supportbot.config.DocumentationAnalysisProps;
import com.coreeng.supportbot.config.LlmProps;
import com.coreeng.supportbot.docanalysis.ClassificationAnswer.Verdict;
import com.coreeng.supportbot.summarydata.ThreadSanitiser;
import com.coreeng.supportbot.summarydata.ThreadService;
import com.coreeng.supportbot.ticket.TicketId;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Classifies one closed ticket against the documentation an open session supplies, checks the
 * answer and saves the result or the failed attempt. A failed model call is returned unsaved.
 */
@Service
@ConditionalOnLlmEnabled
@ConditionalOnProperty(name = "documentation-analysis.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DocClassifier {

    static final String QUOTE_NOT_FOUND = "quote_not_found";
    static final String UNKNOWN_PAGE = "unknown_page";
    static final String NO_SUPPORT_ANSWER = "no_support_answer";
    static final String UNDETERMINED = "undetermined";
    static final String THREAD_UNAVAILABLE = "thread_unavailable";
    static final String SOURCE_ERROR = "source_error";

    static final int MAX_QUOTE_LENGTH = 200;
    static final int MAX_TAGS = 5;

    private static final Pattern TAG = Pattern.compile("[a-z0-9]++(?:-[a-z0-9]++)*+");
    private static final Pattern EMPHASIS = Pattern.compile("[*_`]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final String SUPPORT_LABEL = "[support] ";

    private final ChatModel chatModel;
    private final ThreadService threadService;
    private final ThreadSanitiser sanitiser;
    private final AnalysisService analysisService;
    private final DocAnalysisRepository repository;
    private final DocumentationSource source;
    private final DocumentationAnalysisProps props;
    private final LlmProps llmProps;
    private final Clock clock;

    /**
     * @throws AnalysisPromptLoadException when no classification prompt is in use, which no ticket
     *     can get past
     */
    public ClassificationOutcome classify(DocumentationSession session, TicketToClassify ticket) {
        String prompt = loadPrompt();
        String promptId = promptId(prompt);
        TicketId ticketId = ticket.ticketId();

        String thread;
        try {
            thread = threadService.getThreadAsRoleLabelledText(ticket.channelId(), ticket.threadTs());
        } catch (RuntimeException e) {
            if (Thread.currentThread().isInterrupted()) {
                throw e;
            }
            log.warn("Could not fetch the thread of ticket {}", ticketId, e);
            return failed(ticketId, promptId, THREAD_UNAVAILABLE, true);
        }

        int budget = props.maxInputTokens() - TokenEstimator.estimate(thread) - TokenEstimator.estimate(prompt);
        if (budget <= 0) {
            return failed(ticketId, promptId, ContextAnswer.Failed.CONTEXT_TOO_LARGE, false);
        }

        ContextAnswer context;
        try {
            context = session.findContext(thread, budget);
        } catch (RuntimeException e) {
            log.error("The documentation source failed on ticket {}", ticketId, e);
            return failed(ticketId, promptId, SOURCE_ERROR, true);
        }

        return switch (context) {
            case ContextAnswer.NotADocumentationQuestion answer ->
                saved(result(
                        ticketId,
                        promptId,
                        session,
                        DocClassification.NOT_APPLICABLE,
                        answer.question(),
                        "",
                        List.of(),
                        List.of(),
                        null,
                        answer.reason()));
            case ContextAnswer.NothingCovers answer ->
                saved(result(
                        ticketId,
                        promptId,
                        session,
                        DocClassification.MISSING,
                        answer.question(),
                        "",
                        List.of(),
                        List.of(),
                        null,
                        answer.reason()));
            case ContextAnswer.Failed failure
            when failure.reasonCode().equals(ContextAnswer.Failed.PROVIDER_ERROR) ->
                new ClassificationOutcome.ProviderFailed(ticketId, promptId);
            case ContextAnswer.Failed failure -> failed(ticketId, promptId, failure.reasonCode(), failure.retryable());
            case ContextAnswer.Pages pages ->
                pages.estimatedTokens() > budget
                        ? failed(ticketId, promptId, ContextAnswer.Failed.CONTEXT_TOO_LARGE, false)
                        : classifyAgainst(ticketId, promptId, session, prompt, thread, pages);
        };
    }

    private ClassificationOutcome classifyAgainst(
            TicketId ticketId,
            String promptId,
            DocumentationSession session,
            String prompt,
            String thread,
            ContextAnswer.Pages pages) {
        DistilledQuestion sourceQuestion = pages.question();
        String input = ModelInput.start()
                .group(InputTag.DOCUMENTATION, docs -> pages.pages()
                        .forEach(page -> docs.part(InputTag.PAGE, "path", page.id(), page.text())))
                .part(InputTag.AREAS_SUPPLIED, String.join("\n", pages.areasSupplied()))
                .part(InputTag.AREAS_NOT_SUPPLIED, String.join("\n", pages.areasNotSupplied()))
                .part(InputTag.QUESTION, sourceQuestion == null ? "" : sourceQuestion.text())
                .part(InputTag.THREAD, thread)
                .instructions(prompt);

        ChatResponse response;
        try {
            response = chatModel.chat(request(input));
        } catch (RuntimeException e) {
            log.warn("The classification call failed on ticket {}", ticketId, e);
            return new ClassificationOutcome.ProviderFailed(ticketId, promptId);
        }

        Optional<ClassificationAnswer> parsed =
                ClassificationAnswer.parse(response.aiMessage().text());
        if (parsed.isEmpty()) {
            return failed(ticketId, promptId, ContextAnswer.Failed.INVALID_RESPONSE, true);
        }
        ClassificationAnswer answer = parsed.get();
        String rejection = rejection(answer, pages, thread);
        if (rejection != null) {
            return failed(ticketId, promptId, rejection, true);
        }

        DocClassification classification = classification(answer.verdict());
        boolean cites = classification == DocClassification.CORRECT || classification == DocClassification.INCORRECT;
        List<String> docPaths = classification == DocClassification.NOT_APPLICABLE ? List.of() : answer.docPaths();
        return saved(result(
                ticketId,
                promptId,
                session,
                classification,
                sourceQuestion != null ? sourceQuestion : new DistilledQuestion(answer.question(), answer.tags()),
                answer.answerSummary(),
                countedAreas(pages, docPaths),
                docPaths,
                cites ? answer.evidenceQuote() : null,
                answer.rationale()));
    }

    /**
     * The areas the ticket counts under, the one to count it under first: that of the first page it
     * names, then, when the source chose among areas, the areas it supplied. Judged against the
     * whole documentation, a ticket that names no page has no area.
     */
    private static List<String> countedAreas(ContextAnswer.Pages pages, List<String> docPaths) {
        List<String> areas = new ArrayList<>();
        if (!docPaths.isEmpty()) {
            for (DocumentationPage page : pages.pages()) {
                String area = page.area();
                if (page.id().equals(docPaths.getFirst()) && area != null) {
                    areas.add(area);
                }
            }
        }
        if (!pages.areasNotSupplied().isEmpty()) {
            pages.areasSupplied().stream().filter(area -> !areas.contains(area)).forEach(areas::add);
        }
        return areas;
    }

    /**
     * The {@code prompt_id} a result saved now would carry.
     *
     * @throws AnalysisPromptLoadException when no classification prompt is in use
     */
    public String currentPromptId() {
        return promptId(loadPrompt());
    }

    private ChatRequest request(String input) {
        ChatRequest.Builder request = ChatRequest.builder().messages(UserMessage.from(input));
        if (chatModel.supportedCapabilities().contains(RESPONSE_FORMAT_JSON_SCHEMA)) {
            request.responseFormat(ClassificationAnswer.RESPONSE_FORMAT);
        }
        return request.build();
    }

    /** The reason code the answer is rejected with, or null when it passes every check. */
    private static @Nullable String rejection(ClassificationAnswer answer, ContextAnswer.Pages pages, String thread) {
        Verdict verdict = answer.verdict();
        if (verdict == Verdict.UNDETERMINED) {
            return UNDETERMINED;
        }
        if (pages.question() == null && answer.question().isBlank()) {
            return ContextAnswer.Failed.INVALID_RESPONSE;
        }
        Map<String, DocumentationPage> supplied =
                pages.pages().stream().collect(Collectors.toMap(DocumentationPage::id, Function.identity()));
        if (!supplied.keySet().containsAll(answer.docPaths())) {
            return UNKNOWN_PAGE;
        }
        if (verdict == Verdict.CORRECT || verdict == Verdict.INCORRECT) {
            if (answer.docPaths().size() != 1) {
                return ContextAnswer.Failed.INVALID_RESPONSE;
            }
            String quote = comparable(answer.evidenceQuote());
            if (quote.isEmpty() || quote.length() > MAX_QUOTE_LENGTH) {
                return ContextAnswer.Failed.INVALID_RESPONSE;
            }
            DocumentationPage cited =
                    Objects.requireNonNull(supplied.get(answer.docPaths().getFirst()));
            if (!comparable(ModelInput.removeTags(cited.text())).contains(quote)) {
                return QUOTE_NOT_FOUND;
            }
        }
        if (verdict == Verdict.INCORRECT) {
            if (!hasSupportMessage(thread)) {
                return NO_SUPPORT_ANSWER;
            }
            if (answer.answerSummary().isBlank()) {
                return ContextAnswer.Failed.INVALID_RESPONSE;
            }
        }
        return null;
    }

    /** Line breaks and emphasis markers may differ between a quote and its page; the words may not. */
    private static String comparable(String text) {
        String unmarked = EMPHASIS.matcher(text).replaceAll("");
        return WHITESPACE.matcher(unmarked).replaceAll(" ").strip();
    }

    /** Each message is one line starting with its label, so a label can only open a message. */
    private static boolean hasSupportMessage(String thread) {
        return thread.startsWith(SUPPORT_LABEL) || thread.contains("\n" + SUPPORT_LABEL);
    }

    private static DocClassification classification(Verdict verdict) {
        return switch (verdict) {
            case CORRECT -> DocClassification.CORRECT;
            case MISSING -> DocClassification.MISSING;
            case INCORRECT -> DocClassification.INCORRECT;
            case NOT_APPLICABLE -> DocClassification.NOT_APPLICABLE;
            case UNDETERMINED -> throw new IllegalArgumentException("undetermined is not a classification");
        };
    }

    private DocAnalysis.Result result(
            TicketId ticketId,
            String promptId,
            DocumentationSession session,
            DocClassification classification,
            DistilledQuestion question,
            String answerSummary,
            List<String> productAreas,
            List<String> docPaths,
            @Nullable String evidenceQuote,
            String rationale) {
        String summary = sanitiser.sanitise(answerSummary);
        return new DocAnalysis.Result(
                ticketId,
                promptId,
                classification,
                props.source().selectedType().configValue(),
                session.version(),
                sanitiser.sanitise(question.text()),
                summary.isEmpty() ? null : summary,
                tags(question.tags()),
                productAreas,
                docPaths,
                evidenceQuote,
                sanitiser.sanitise(rationale),
                modelName(),
                clock.instant());
    }

    /** Never fails a ticket: a tag in any other form than lowercase words joined by hyphens is dropped. */
    private List<String> tags(List<String> tags) {
        return tags.stream()
                .map(tag -> sanitiser.sanitise(tag).toLowerCase(Locale.ROOT))
                .filter(tag -> TAG.matcher(tag).matches())
                .distinct()
                .limit(MAX_TAGS)
                .toList();
    }

    private ClassificationOutcome.Classified saved(DocAnalysis.Result result) {
        repository.saveResult(result);
        log.info(
                "Ticket {} classified against the documentation as {}",
                result.ticketId(),
                result.classification().code());
        return new ClassificationOutcome.Classified(result);
    }

    private ClassificationOutcome.AttemptFailed failed(
            TicketId ticketId, String promptId, String reasonCode, boolean retryable) {
        DocAnalysis.FailedAttempt attempt = recordFailedAttempt(ticketId, promptId, reasonCode, retryable);
        log.info(
                "Ticket {} failed documentation classification: {} (attempt {})",
                ticketId,
                reasonCode,
                attempt.attempts());
        return new ClassificationOutcome.AttemptFailed(attempt, retryable);
    }

    /** Counts on from the ticket's previous failed attempt under the same prompt id, and from one otherwise. */
    DocAnalysis.FailedAttempt recordFailedAttempt(TicketId ticketId, String promptId, String reasonCode) {
        return recordFailedAttempt(ticketId, promptId, reasonCode, true);
    }

    /**
     * A failure that trying again cannot help is saved as out of attempts, so the ticket waits for
     * the prompt id to change; the table has no column of its own for it.
     */
    private DocAnalysis.FailedAttempt recordFailedAttempt(
            TicketId ticketId, String promptId, String reasonCode, boolean retryable) {
        int previous = repository
                .findByTicketId(ticketId)
                .filter(row -> row instanceof DocAnalysis.FailedAttempt
                        && row.promptId().equals(promptId))
                .map(row -> ((DocAnalysis.FailedAttempt) row).attempts())
                .orElse(0);
        int attempts = retryable ? previous + 1 : Math.max(previous + 1, props.maxAttempts());
        DocAnalysis.FailedAttempt attempt =
                new DocAnalysis.FailedAttempt(ticketId, promptId, clock.instant(), reasonCode, attempts);
        repository.saveFailedAttempt(attempt);
        return attempt;
    }

    private String loadPrompt() {
        AnalysisPrompt prompt = analysisService.inUsePrompt(AnalysisPromptType.DOCUMENTATION_CLASSIFICATION);
        if (prompt == null) {
            throw new AnalysisPromptLoadException("No documentation classification prompt version is marked as in use");
        }
        return prompt.content();
    }

    private String promptId(String prompt) {
        return Fingerprint.of(List.of(prompt, Integer.toString(props.maxInputTokens()), source.fingerprint()));
    }

    /** As the model reports itself, so the stub's output is never stamped with a real model's name. */
    private String modelName() {
        String reported = chatModel.defaultRequestParameters().modelName();
        return reported != null && !reported.isBlank() ? reported : llmProps.modelName();
    }
}
