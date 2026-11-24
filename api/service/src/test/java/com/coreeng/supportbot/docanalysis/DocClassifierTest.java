package com.coreeng.supportbot.docanalysis;

import static com.coreeng.supportbot.docanalysis.StubDocumentationSource.page;
import static com.coreeng.supportbot.docanalysis.StubDocumentationSource.pages;
import static com.coreeng.supportbot.docanalysis.StubDocumentationSource.question;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.analysis.AnalysisPrompt;
import com.coreeng.supportbot.analysis.AnalysisPromptType;
import com.coreeng.supportbot.analysis.AnalysisService;
import com.coreeng.supportbot.analysis.llm.StubChatModel;
import com.coreeng.supportbot.config.DocumentationAnalysisProps;
import com.coreeng.supportbot.config.LlmProps;
import com.coreeng.supportbot.config.LlmProvider;
import com.coreeng.supportbot.config.SummaryAreaProps;
import com.coreeng.supportbot.summarydata.ThreadSanitiser;
import com.coreeng.supportbot.summarydata.ThreadService;
import com.coreeng.supportbot.ticket.TicketId;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.util.unit.DataSize;

class DocClassifierTest {

    private static final TicketToClassify TICKET = new TicketToClassify(new TicketId(42), "C123", "1700000000.000100");
    private static final String PROMPT = "Classify the thread against the documentation.";
    private static final int MAX_INPUT_TOKENS = 10_000;
    private static final Instant NOW = Instant.parse("2026-11-20T10:00:00Z");
    private static final String EMAIL_PATTERN = "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}";

    private static final String THREAD =
            "[tenant] How do I rotate my deploy key?\n\n[support] Open a ticket and we rotate it for you.";
    private static final String THREAD_WITHOUT_SUPPORT =
            "[tenant] How do I rotate my deploy key?\n\n[bot] Ticket created.";

    private static final DocumentationPage KEYS = page(
            "guides/keys.md",
            "access",
            "# Deploy keys\n\nTo rotate a key, run **the rotate\ncommand** from the `console`.\n");
    private static final DocumentationPage BUILDS =
            page("guides/builds.md", "builds", "# Builds\n\nBuilds run on every push.\n");
    private static final ContextAnswer.Pages AREA_PAGES =
            new ContextAnswer.Pages(List.of(KEYS, BUILDS), List.of("access", "builds"), List.of("networking"), null);
    private static final String QUOTE = "To rotate a key, run the rotate command from the console.";

    private final FakeChatModel model = new FakeChatModel();
    private final InMemoryDocAnalysisRepository repository = new InMemoryDocAnalysisRepository();
    private final ThreadService threadService = mock(ThreadService.class);
    private StubDocumentationSource source = StubDocumentationSource.answering(AREA_PAGES);

    @Test
    void savesNotApplicableWithoutAModelCallWhenTheSourceSaysItIsNoDocumentationQuestion() {
        source.answer(new ContextAnswer.NotADocumentationQuestion(
                "The tenant asks for access.", question("Can I get access to the cluster?", "cluster-access")));

        DocAnalysis.Result result = classified(classify(THREAD));

        assertThat(result.classification()).isEqualTo(DocClassification.NOT_APPLICABLE);
        assertThat(result.rationale()).isEqualTo("The tenant asks for access.");
        assertThat(result.question()).isEqualTo("Can I get access to the cluster?");
        assertThat(result.tags()).containsExactly("cluster-access");
        assertThat(result.docPaths()).isEmpty();
        assertThat(result.evidenceQuote()).isNull();
        assertThat(model.requests).isEmpty();
        assertThat(repository.findByTicketId(TICKET.ticketId())).contains(result);
    }

    @Test
    void savesMissingWithNoAreasWithoutAModelCallWhenTheSourceSaysNothingCoversIt() {
        source.answer(new ContextAnswer.NothingCovers(
                "No page covers deploy keys.", question("How do I rotate a deploy key?", "deploy-key")));

        DocAnalysis.Result result = classified(classify(THREAD));

        assertThat(result.classification()).isEqualTo(DocClassification.MISSING);
        assertThat(result.rationale()).isEqualTo("No page covers deploy keys.");
        assertThat(result.productAreas()).isEmpty();
        assertThat(result.docPaths()).isEmpty();
        assertThat(model.requests).isEmpty();
    }

    @Test
    void recordsTheSourcesOwnReasonWhenTheSourceFails() {
        source.answer(new ContextAnswer.Failed("area_too_large", false));

        ClassificationOutcome.AttemptFailed failed = attemptFailed(classify(THREAD));

        assertThat(failed.attempt().reasonCode()).isEqualTo("area_too_large");
        assertThat(failed.retryable()).isFalse();
        assertThat(model.requests).isEmpty();
        assertThat(repository.findByTicketId(TICKET.ticketId())).contains(failed.attempt());
    }

    @Test
    void recordsContextTooLargeWithoutAModelCallWhenTheSourceReturnsMoreThanTheBudget() {
        int budget = MAX_INPUT_TOKENS - TokenEstimator.estimate(THREAD) - TokenEstimator.estimate(PROMPT);
        source.answer(pages(page("huge.md", null, "x".repeat(budget * 4))));

        ClassificationOutcome.AttemptFailed failed = attemptFailed(classify(THREAD));

        assertThat(failed.attempt().reasonCode()).isEqualTo(ContextAnswer.Failed.CONTEXT_TOO_LARGE);
        assertThat(failed.retryable()).isFalse();
        assertThat(model.requests).isEmpty();
    }

    @Test
    void recordsContextTooLargeWithoutAskingTheSourceWhenTheThreadLeavesNoBudget() {
        String longThread = "[tenant] " + "word ".repeat(MAX_INPUT_TOKENS * 2);

        ClassificationOutcome.AttemptFailed failed = attemptFailed(classify(longThread));

        assertThat(failed.attempt().reasonCode()).isEqualTo(ContextAnswer.Failed.CONTEXT_TOO_LARGE);
        assertThat(source.requests()).isEmpty();
    }

    @Test
    void asksTheSourceWithTheBudgetLeftAfterTheThreadAndThePrompt() {
        model.answer(json(answer("missing")));

        classify(THREAD);

        assertThat(source.requests())
                .containsExactly(new StubDocumentationSource.Request(
                        THREAD, MAX_INPUT_TOKENS - TokenEstimator.estimate(THREAD) - TokenEstimator.estimate(PROMPT)));
    }

    @Test
    void savesACorrectResultCitingASuppliedPageAndAPassageItContains() {
        Map<String, Object> answer = answer("correct");
        answer.put("doc_paths", List.of(KEYS.id()));
        answer.put("evidence_quote", "To rotate a key, run **the rotate\ncommand** from the `console`.");
        model.answer(json(answer));

        DocAnalysis.Result result = classified(classify(THREAD));

        assertThat(result.classification()).isEqualTo(DocClassification.CORRECT);
        assertThat(result.docPaths()).containsExactly(KEYS.id());
        assertThat(result.evidenceQuote())
                .isEqualTo("To rotate a key, run **the rotate\ncommand** from the `console`.");
        assertThat(result.productAreas()).containsExactly("access", "builds");
        assertThat(result.source()).isEqualTo("github");
        assertThat(result.docsVersion()).isEqualTo(StubDocumentationSource.DEFAULT_VERSION);
        assertThat(result.model()).isEqualTo(FakeChatModel.MODEL_NAME);
        assertThat(result.analysedAt()).isEqualTo(NOW);
    }

    @Test
    void acceptsAQuoteThatDiffersFromThePageOnlyInLineBreaksAndEmphasisMarkers() {
        Map<String, Object> answer = answer("correct");
        answer.put("doc_paths", List.of(KEYS.id()));
        answer.put("evidence_quote", QUOTE);
        model.answer(json(answer));

        assertThat(classified(classify(THREAD)).evidenceQuote()).isEqualTo(QUOTE);
    }

    @Test
    void savesAMissingResultWithTheClosestPagesAndTheAreasConsulted() {
        Map<String, Object> answer = answer("missing");
        answer.put("doc_paths", List.of(KEYS.id(), BUILDS.id()));
        answer.put("evidence_quote", "ignored for missing");
        model.answer(json(answer));

        DocAnalysis.Result result = classified(classify(THREAD));

        assertThat(result.classification()).isEqualTo(DocClassification.MISSING);
        assertThat(result.docPaths()).containsExactly(KEYS.id(), BUILDS.id());
        assertThat(result.productAreas()).containsExactly("access", "builds");
        assertThat(result.evidenceQuote()).isNull();
    }

    @Test
    void savesAnIncorrectResultWithSupportsAnswer() {
        Map<String, Object> answer = answer("incorrect");
        answer.put("doc_paths", List.of(KEYS.id()));
        answer.put("evidence_quote", QUOTE);
        answer.put("answer_summary", "Support said only the platform team can rotate keys.");
        model.answer(json(answer));

        DocAnalysis.Result result = classified(classify(THREAD));

        assertThat(result.classification()).isEqualTo(DocClassification.INCORRECT);
        assertThat(result.answerSummary()).isEqualTo("Support said only the platform team can rotate keys.");
        assertThat(result.docPaths()).containsExactly(KEYS.id());
    }

    @Test
    void savesNotApplicableFromTheModelWhenTheSourceDidNotJudgeTheThread() {
        Map<String, Object> answer = answer("not_applicable");
        answer.put("doc_paths", List.of(KEYS.id()));
        answer.put("evidence_quote", QUOTE);
        model.answer(json(answer));

        DocAnalysis.Result result = classified(classify(THREAD));

        assertThat(result.classification()).isEqualTo(DocClassification.NOT_APPLICABLE);
        assertThat(result.docPaths()).isEmpty();
        assertThat(result.evidenceQuote()).isNull();
    }

    @Test
    void rejectsAQuoteThatIsNotInTheCitedPage() {
        Map<String, Object> answer = answer("correct");
        answer.put("doc_paths", List.of(KEYS.id()));
        answer.put("evidence_quote", "Builds run on every push.");
        model.answer(json(answer));

        assertThat(failureReason(classify(THREAD))).isEqualTo(DocClassifier.QUOTE_NOT_FOUND);
    }

    @Test
    void rejectsACitedPageTheSourceDidNotReturn() {
        Map<String, Object> answer = answer("correct");
        answer.put("doc_paths", List.of("guides/invented.md"));
        answer.put("evidence_quote", QUOTE);
        model.answer(json(answer));

        assertThat(failureReason(classify(THREAD))).isEqualTo(DocClassifier.UNKNOWN_PAGE);
    }

    @Test
    void rejectsAClosestPageTheSourceDidNotReturn() {
        Map<String, Object> answer = answer("missing");
        answer.put("doc_paths", List.of(KEYS.id(), "guides/invented.md"));
        model.answer(json(answer));

        assertThat(failureReason(classify(THREAD))).isEqualTo(DocClassifier.UNKNOWN_PAGE);
    }

    @Test
    void rejectsIncorrectForAThreadWithNoSupportMessage() {
        Map<String, Object> answer = answer("incorrect");
        answer.put("doc_paths", List.of(KEYS.id()));
        answer.put("evidence_quote", QUOTE);
        answer.put("answer_summary", "The tenant was told to open a ticket.");
        model.answer(json(answer));

        assertThat(failureReason(classify(THREAD_WITHOUT_SUPPORT))).isEqualTo(DocClassifier.NO_SUPPORT_ANSWER);
    }

    @Test
    void recordsUndeterminedAsAFailedAttemptNotAsMissing() {
        model.answer(json(answer("undetermined")));

        ClassificationOutcome.AttemptFailed failed = attemptFailed(classify(THREAD));

        assertThat(failed.attempt().reasonCode()).isEqualTo(DocClassifier.UNDETERMINED);
        assertThat(failed.retryable()).isTrue();
    }

    static Stream<Arguments> invalidAnswers() {
        Map<String, Object> unknownClassification = answer("partly_correct");
        Map<String, Object> twoPages = answer("correct");
        twoPages.put("doc_paths", List.of(KEYS.id(), BUILDS.id()));
        twoPages.put("evidence_quote", QUOTE);
        Map<String, Object> noQuote = answer("correct");
        noQuote.put("doc_paths", List.of(KEYS.id()));
        Map<String, Object> longQuote = answer("correct");
        longQuote.put("doc_paths", List.of(KEYS.id()));
        longQuote.put("evidence_quote", "a".repeat(DocClassifier.MAX_QUOTE_LENGTH + 1));
        Map<String, Object> noSummary = answer("incorrect");
        noSummary.put("doc_paths", List.of(KEYS.id()));
        noSummary.put("evidence_quote", QUOTE);
        Map<String, Object> noQuestion = answer("missing");
        noQuestion.remove("question");
        Map<String, Object> noRationale = answer("missing");
        noRationale.put("rationale", " ");
        Map<String, Object> pathsNotAList = answer("missing");
        pathsNotAList.put("doc_paths", KEYS.id());
        return Stream.of(
                Arguments.of("prose", "The documentation answers this."),
                Arguments.of("truncated JSON", "{\"classification\": \"correct\""),
                Arguments.of("an array", "[]"),
                Arguments.of("text after the object", json(answer("missing")) + " Hope this helps."),
                Arguments.of("a classification outside the set", json(unknownClassification)),
                Arguments.of("correct citing two pages", json(twoPages)),
                Arguments.of("correct without a quote", json(noQuote)),
                Arguments.of("a quote over 200 characters", json(longQuote)),
                Arguments.of("incorrect without support's answer", json(noSummary)),
                Arguments.of("no question when the source gave none", json(noQuestion)),
                Arguments.of("a blank rationale", json(noRationale)),
                Arguments.of("doc_paths that is not a list", json(pathsNotAList)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidAnswers")
    void recordsInvalidResponseForAnAnswerThatBreaksTheContract(String description, String text) {
        model.answer(text);

        assertThat(failureReason(classify(THREAD))).isEqualTo(ContextAnswer.Failed.INVALID_RESPONSE);
    }

    @Test
    void returnsAFailedModelCallUnsaved() {
        model.failure = new RateLimitException("slow down");

        ClassificationOutcome outcome = classify(THREAD);

        assertThat(outcome).isInstanceOf(ClassificationOutcome.ProviderFailed.class);
        assertThat(((ClassificationOutcome.ProviderFailed) outcome).ticketId()).isEqualTo(TICKET.ticketId());
        assertThat(repository.findByTicketId(TICKET.ticketId())).isEmpty();
    }

    @Test
    void returnsAProviderErrorFromTheSourceUnsaved() {
        source.answer(ContextAnswer.Failed.providerError());

        assertThat(classify(THREAD)).isInstanceOf(ClassificationOutcome.ProviderFailed.class);
        assertThat(repository.findByTicketId(TICKET.ticketId())).isEmpty();
    }

    @Test
    void recordsAnExceptionFromTheSourceAsAFailedAttempt() {
        DocumentationSession throwing = new DocumentationSession() {
            @Override
            public String version() {
                return "v1";
            }

            @Override
            public ContextAnswer findContext(String thread, int budget) {
                throw new IllegalStateException("defect");
            }
        };

        assertThat(failureReason(classifier().classify(throwing, threadReturning(THREAD))))
                .isEqualTo(DocClassifier.SOURCE_ERROR);
    }

    @Test
    void recordsAThreadThatCannotBeFetchedAsAFailedAttempt() {
        when(threadService.getThreadAsRoleLabelledText(TICKET.channelId(), TICKET.threadTs()))
                .thenThrow(new IllegalStateException("thread_not_found"));

        assertThat(failureReason(classifier().classify(session(), TICKET))).isEqualTo(DocClassifier.THREAD_UNAVAILABLE);
        assertThat(source.requests()).isEmpty();
    }

    @Test
    void acceptsAnAnswerInsideACodeFence() {
        model.answer("```json\n" + json(answer("missing")) + "\n```");

        assertThat(classified(classify(THREAD)).classification()).isEqualTo(DocClassification.MISSING);
    }

    @Test
    void asksForSchemaOutputFromAClientThatSupportsIt() {
        FakeChatModel schemaModel = new FakeChatModel(Capability.RESPONSE_FORMAT_JSON_SCHEMA);
        schemaModel.answer(json(answer("missing")));

        classified(classifier(schemaModel, MAX_INPUT_TOKENS).classify(session(), threadReturning(THREAD)));

        var format = schemaModel.requests.getFirst().responseFormat();
        assertThat(format).isNotNull();
        assertThat(format.type()).isEqualTo(ResponseFormatType.JSON);
        assertThat(format.jsonSchema()).isNotNull();
    }

    @Test
    void asksForNoSchemaFromAClientThatDoesNotSupportIt() {
        model.answer(json(answer("missing")));

        classify(THREAD);

        assertThat(model.requests.getFirst().responseFormat()).isNull();
    }

    @Test
    void laysOutTheDocumentationInTheSourcesOrderThenTheAreasQuestionAndThreadThenTheInstructions() {
        source.answer(new ContextAnswer.Pages(
                List.of(BUILDS, KEYS),
                List.of("builds", "access"),
                List.of("networking"),
                question("How do I rotate a deploy key?", "deploy-key")));
        model.answer(json(answer("missing")));

        classify(THREAD);

        String sent = model.lastPrompt();
        assertThat(sent).startsWith("<documentation>\n<page path=\"guides/builds.md\">");
        assertThat(sent).endsWith(PROMPT);
        assertThat(sent)
                .containsSubsequence(
                        "<page path=\"guides/builds.md\">",
                        "<page path=\"guides/keys.md\">",
                        "</documentation>",
                        "<areas_supplied>\nbuilds\naccess\n</areas_supplied>",
                        "<areas_not_supplied>\nnetworking\n</areas_not_supplied>",
                        "<question>\nHow do I rotate a deploy key?\n</question>",
                        "<thread>\n" + THREAD + "\n</thread>",
                        PROMPT);
    }

    @Test
    void sendsAnEmptyQuestionAndEmptyAreasForASourceWithoutThem() {
        source.answer(pages(page("index.md", null, QUOTE)));
        Map<String, Object> answer = answer("correct");
        answer.put("doc_paths", List.of("index.md"));
        answer.put("evidence_quote", QUOTE);
        model.answer(json(answer));

        DocAnalysis.Result result = classified(classify(THREAD));

        assertThat(model.lastPrompt())
                .contains("<areas_supplied>\n</areas_supplied>")
                .contains("<areas_not_supplied>\n</areas_not_supplied>")
                .contains("<question>\n</question>");
        assertThat(result.productAreas()).isEmpty();
    }

    @Test
    void takesTheQuestionAndTagsFromTheAnswerWhenTheSourceGaveNone() {
        Map<String, Object> answer = answer("missing");
        answer.put("question", "How does a tenant rotate a deploy key?");
        answer.put("tags", List.of("deploy-key", "rotate-credentials"));
        model.answer(json(answer));

        DocAnalysis.Result result = classified(classify(THREAD));

        assertThat(result.question()).isEqualTo("How does a tenant rotate a deploy key?");
        assertThat(result.tags()).containsExactly("deploy-key", "rotate-credentials");
    }

    @Test
    void takesTheQuestionAndTagsFromTheSourceWhenItGaveThem() {
        source.answer(new ContextAnswer.Pages(
                List.of(KEYS), List.of("access"), List.of(), question("Can keys be rotated?", "key-rotation")));
        Map<String, Object> answer = answer("missing");
        answer.put("question", "Something else entirely?");
        answer.put("tags", List.of("other-tag"));
        model.answer(json(answer));

        DocAnalysis.Result result = classified(classify(THREAD));

        assertThat(result.question()).isEqualTo("Can keys be rotated?");
        assertThat(result.tags()).containsExactly("key-rotation");
    }

    @Test
    void keepsOnlyDistinctLowercaseHyphenatedTagsAndAtMostFive() {
        Map<String, Object> answer = answer("missing");
        answer.put(
                "tags",
                List.of(
                        "deploy-key",
                        "Rotate Credentials",
                        "DEPLOY-KEY",
                        "under_score",
                        "-leading",
                        "a",
                        "b",
                        "c",
                        "d",
                        "e"));
        model.answer(json(answer));

        assertThat(classified(classify(THREAD)).tags()).containsExactly("deploy-key", "a", "b", "c", "d");
    }

    @Test
    void savesWithoutTagsWhenTheTagsAreMissingOrMalformed() {
        Map<String, Object> missing = answer("missing");
        missing.remove("tags");
        model.answer(json(missing));
        assertThat(classified(classify(THREAD)).tags()).isEmpty();

        Map<String, Object> malformed = answer("missing");
        malformed.put("tags", "deploy-key, rotate");
        model.answer(json(malformed));
        assertThat(classified(classify(THREAD)).tags()).isEmpty();

        Map<String, Object> notStrings = answer("missing");
        notStrings.put("tags", List.of(1, 2));
        model.answer(json(notStrings));
        assertThat(classified(classify(THREAD)).tags()).isEmpty();
    }

    @Test
    void sanitisesTheStoredQuestionTagsAnswerSummaryAndRationale() {
        Map<String, Object> answer = answer("incorrect");
        answer.put("doc_paths", List.of(KEYS.id()));
        answer.put("evidence_quote", QUOTE);
        answer.put("question", "Why did jo@example.com fail to rotate a key?");
        answer.put("tags", List.of("deploy-key", "jo@example.com"));
        answer.put("answer_summary", "Support told jo@example.com to open a ticket.");
        answer.put("rationale", "The page contradicts what jo@example.com was told.");
        model.answer(json(answer));

        DocAnalysis.Result result = classified(classify(THREAD));

        assertThat(result.question()).isEqualTo("Why did fail to rotate a key?");
        assertThat(result.tags()).containsExactly("deploy-key");
        assertThat(result.answerSummary()).isEqualTo("Support told to open a ticket.");
        assertThat(result.rationale()).isEqualTo("The page contradicts what was told.");
    }

    @Test
    void changesThePromptIdWithTheSourcesFingerprint() {
        model.answer(json(answer("missing")));
        String first = classified(classify(THREAD)).promptId();

        source = StubDocumentationSource.answering(AREA_PAGES).fingerprint("another-fingerprint");
        String second = classified(classify(THREAD)).promptId();

        assertThat(second).isNotEqualTo(first);
        assertThat(first)
                .isEqualTo(Fingerprint.of(List.of(
                        PROMPT, Integer.toString(MAX_INPUT_TOKENS), StubDocumentationSource.DEFAULT_FINGERPRINT)));
    }

    @Test
    void countsFailedAttemptsOnUnderTheSamePromptId() {
        model.answer(json(answer("undetermined")));

        int first = attemptFailed(classify(THREAD)).attempt().attempts();
        int second = attemptFailed(classify(THREAD)).attempt().attempts();

        assertThat(first).isEqualTo(1);
        assertThat(second).isEqualTo(2);
    }

    @Test
    void savesAFailureThatTryingAgainCannotHelpAsOutOfAttempts() {
        source.answer(new ContextAnswer.Failed("area_too_large", false));

        assertThat(attemptFailed(classify(THREAD)).attempt().attempts()).isEqualTo(3);
        assertThat(repository.findByTicketId(TICKET.ticketId()))
                .get()
                .extracting(row -> ((DocAnalysis.FailedAttempt) row).attempts())
                .isEqualTo(3);
    }

    @Test
    void reportsThePromptIdResultsAreSavedUnder() {
        source.answer(new ContextAnswer.NothingCovers("No page covers it.", question("How?", "how")));

        assertThat(classifier().currentPromptId())
                .isEqualTo(classified(classify(THREAD)).promptId());
    }

    @Test
    void countsFromOneAfterAFailureUnderAnotherPromptId() {
        repository.saveFailedAttempt(new DocAnalysis.FailedAttempt(TICKET.ticketId(), "old-prompt-id", NOW, "x", 3));
        model.answer(json(answer("undetermined")));

        assertThat(attemptFailed(classify(THREAD)).attempt().attempts()).isEqualTo(1);
    }

    @Test
    void theStubModelsAnswersPassTheChecks() {
        Set<DocClassification> seen = new HashSet<>();
        for (int i = 0; i < 12; i++) {
            String thread = "[tenant] Question number " + i + "\n\n[support] An answer.";
            DocAnalysis.Result result = classified(
                    classifier(new StubChatModel(), MAX_INPUT_TOKENS).classify(session(), threadReturning(thread)));
            seen.add(result.classification());
            assertThat(result.model()).isEqualTo(StubChatModel.MODEL_NAME);
        }

        assertThat(seen).contains(DocClassification.CORRECT, DocClassification.MISSING);
    }

    private ClassificationOutcome classify(String thread) {
        return classifier().classify(session(), threadReturning(thread));
    }

    private TicketToClassify threadReturning(String thread) {
        when(threadService.getThreadAsRoleLabelledText(TICKET.channelId(), TICKET.threadTs()))
                .thenReturn(thread);
        return TICKET;
    }

    private DocumentationSession session() {
        return ((OpenResult.Opened) source.open()).session();
    }

    private DocClassifier classifier() {
        return classifier(model, MAX_INPUT_TOKENS);
    }

    private DocClassifier classifier(ChatModel chatModel, int maxInputTokens) {
        AnalysisService analysisService = mock(AnalysisService.class);
        when(analysisService.inUsePrompt(AnalysisPromptType.DOCUMENTATION_CLASSIFICATION))
                .thenReturn(new AnalysisPrompt(1, PROMPT));
        return new DocClassifier(
                chatModel,
                threadService,
                new ThreadSanitiser(summaryAreaProps()),
                analysisService,
                repository,
                source,
                props(maxInputTokens),
                llmProps(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Map<String, Object> answer(String classification) {
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("classification", classification);
        answer.put("question", "How do I rotate a deploy key?");
        answer.put("tags", List.of("deploy-key"));
        answer.put("answer_summary", "");
        answer.put("doc_paths", List.of());
        answer.put("evidence_quote", "");
        answer.put("rationale", "The supplied pages were checked.");
        return answer;
    }

    private static String json(Map<String, Object> answer) {
        try {
            return new ObjectMapper().writeValueAsString(answer);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static DocAnalysis.Result classified(ClassificationOutcome outcome) {
        assertThat(outcome).isInstanceOf(ClassificationOutcome.Classified.class);
        return ((ClassificationOutcome.Classified) outcome).result();
    }

    private static ClassificationOutcome.AttemptFailed attemptFailed(ClassificationOutcome outcome) {
        assertThat(outcome).isInstanceOf(ClassificationOutcome.AttemptFailed.class);
        return (ClassificationOutcome.AttemptFailed) outcome;
    }

    private String failureReason(ClassificationOutcome outcome) {
        ClassificationOutcome.AttemptFailed failed = attemptFailed(outcome);
        assertThat(repository.findByTicketId(TICKET.ticketId())).contains(failed.attempt());
        return failed.attempt().reasonCode();
    }

    private static SummaryAreaProps summaryAreaProps() {
        return new SummaryAreaProps(
                new SummaryAreaProps.Sanitisation(List.of(EMAIL_PATTERN), List.of()),
                new SummaryAreaProps.Page(1000, Duration.ofMinutes(15)),
                new SummaryAreaProps.OfflineExport("classpath:placeholder-analysis-bundle.zip"));
    }

    private static DocumentationAnalysisProps props(int maxInputTokens) {
        return new DocumentationAnalysisProps(
                false,
                maxInputTokens,
                500,
                Duration.ofHours(24),
                3,
                Duration.ofMinutes(15),
                new DocumentationAnalysisProps.Source(
                        "github",
                        new DocumentationAnalysisProps.GitHub(
                                "",
                                "main",
                                "docs",
                                List.of(".md"),
                                "/tmp/documentation-analysis",
                                Duration.ofHours(24),
                                DataSize.ofMegabytes(50),
                                5000,
                                "whole",
                                2)));
    }

    private static LlmProps llmProps() {
        return new LlmProps(
                LlmProvider.NONE,
                "configured-model",
                Duration.ofMillis(1),
                new LlmProps.Vertex("", ""),
                new LlmProps.Proxy("", new LlmProps.Proxy.Auth(""), Duration.ofSeconds(30)),
                new LlmProps.Anthropic("", "", Duration.ofSeconds(60), 16000),
                new LlmProps.Stub(false));
    }

    private static final class FakeChatModel implements ChatModel {

        static final String MODEL_NAME = "test-model";

        private final Set<Capability> capabilities;
        private final List<ChatRequest> requests = new ArrayList<>();
        private String answer = "";
        private @Nullable RuntimeException failure;

        FakeChatModel(Capability... capabilities) {
            this.capabilities = Set.of(capabilities);
        }

        void answer(String answer) {
            this.answer = answer;
        }

        String lastPrompt() {
            return ((UserMessage) requests.getLast().messages().getFirst()).singleText();
        }

        @Override
        public ChatResponse doChat(ChatRequest request) {
            requests.add(request);
            if (failure != null) {
                throw failure;
            }
            return ChatResponse.builder().aiMessage(AiMessage.from(answer)).build();
        }

        @Override
        public Set<Capability> supportedCapabilities() {
            return capabilities;
        }

        @Override
        public ChatRequestParameters defaultRequestParameters() {
            return ChatRequestParameters.builder().modelName(MODEL_NAME).build();
        }
    }

    private static final class InMemoryDocAnalysisRepository implements DocAnalysisRepository {

        private final Map<TicketId, DocAnalysis> rows = new HashMap<>();

        @Override
        public void saveResult(DocAnalysis.Result result) {
            rows.put(result.ticketId(), result);
        }

        @Override
        public void saveFailedAttempt(DocAnalysis.FailedAttempt attempt) {
            rows.put(attempt.ticketId(), attempt);
        }

        @Override
        public Optional<DocAnalysis> findByTicketId(TicketId ticketId) {
            return Optional.ofNullable(rows.get(ticketId));
        }

        @Override
        public List<TicketToClassify> findDue(
                LocalDate from,
                LocalDate to,
                Collection<String> channelIds,
                String promptId,
                Instant failedBefore,
                int maxAttempts) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int countAttemptedSince(Instant since) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteFailedAttempts(Collection<TicketId> ticketIds, String reasonCode) {
            throw new UnsupportedOperationException();
        }
    }
}
