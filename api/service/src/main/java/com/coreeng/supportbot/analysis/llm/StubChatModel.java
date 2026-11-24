package com.coreeng.supportbot.analysis.llm;

import com.coreeng.supportbot.docanalysis.InputTag;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * A {@link ChatModel} that reaches no network and returns canned, deterministic text.
 *
 * <p>For local development and demos: it lets the analysis run and the Support Summary page be
 * exercised end to end with no credentials and no LLM spend. Every response is a pure function of
 * the request, so a re-run produces identical classifications — which also keeps the summary's
 * data fingerprint stable instead of invalidating the cache on every visit.
 *
 * <p>Local development and demo only. Its classifications and summaries are stored exactly like
 * real ones, so it must never run against a shared database; {@code LlmProps.Stub} enforces
 * the explicit opt-in. Every response — and {@link #defaultRequestParameters()} — reports
 * {@value #MODEL_NAME} as the model name so stored rows carry the stub's name rather than whichever
 * real model id happens to be configured.
 *
 * <h2>Telling the callers apart</h2>
 *
 * Every consumer sends a single blob, so the stub has to work out which one it is answering. The
 * documentation classification is laid out by {@code ModelInput} and opens with its
 * {@code <documentation>} tag. The other two are told apart by the delimiter the summary call site
 * wraps its report in, because that string is owned by our own code ({@code LlmSummaryService})
 * rather than by prompt text stored in the database — prompts get rewritten by migration,
 * delimiters do not. Anything without either marker is treated as a classification request: that
 * is the safer default, since an unrecognised classification would produce unparseable output and
 * silently skip every ticket, while an unrecognised summary merely produces odd prose.
 * {@code StubChatModelTest} and {@code DocClassifierTest} drive the real {@code LlmAnalysisService},
 * {@code LlmSummaryService} and {@code DocClassifier} rather than hand-built strings, so a change to
 * any of these markers fails there instead of quietly degrading.
 */
public class StubChatModel implements ChatModel {

    /** The model name the stub reports on every response, and the one stored against its output. */
    public static final String MODEL_NAME = "stub";

    /** Set by {@code LlmSummaryService} around the aggregated window report. */
    private static final String SUMMARY_MARKER = "--- BEGIN WINDOW REPORT ---";

    private static final String DOCUMENTATION_MARKER = "<" + InputTag.DOCUMENTATION.tagName() + ">";
    private static final Pattern PAGE = Pattern.compile(
            "<" + InputTag.PAGE.tagName() + " path=\"([^\"]*)\">\n(.*?)</" + InputTag.PAGE.tagName() + ">",
            Pattern.DOTALL);
    private static final Pattern QUESTION = Pattern.compile(
            "<" + InputTag.QUESTION.tagName() + ">\n(.*?)</" + InputTag.QUESTION.tagName() + ">", Pattern.DOTALL);
    private static final int MAX_QUOTE_LENGTH = 200;
    private static final ObjectMapper JSON = new ObjectMapper();

    // Values drawn from the controlled taxonomy in the seeded classification prompt (V43), so the
    // stubbed breakdowns look like real ones rather than free text the UI has never seen.
    private static final List<String> DRIVERS = List.of(
            "Knowledge Gap",
            "Product Usability Problem",
            "Product Temporary Issue",
            "Product Bug",
            "Feature Request",
            "Task Request");

    private static final List<String> CATEGORIES = List.of(
            "Tenancy & Onboarding",
            "Build & CI",
            "Deployment & CD",
            "Connectivity & Networking",
            "Platform Tooling",
            "Deploying & Configuring Applications",
            "Monitoring & Troubleshooting",
            "Security & Compliance",
            "Observability");

    private static final List<String> FEATURES = List.of(
            "deployment pipelines",
            "ingress routing",
            "secrets management",
            "workload compute",
            "log aggregation",
            "identity provider groups",
            "None");

    /** A reason line of the summary's window report, which starts with its ticket id. */
    private static final Pattern REPORT_TICKET = Pattern.compile("^- #(\\d+) \\|", Pattern.MULTILINE);

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        String prompt = textOf(chatRequest.messages());
        String answer;
        if (prompt.startsWith(DOCUMENTATION_MARKER)) {
            answer = documentationClassification(prompt);
        } else if (prompt.contains(SUMMARY_MARKER)) {
            answer = summary(prompt);
        } else {
            answer = classification(prompt);
        }
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(answer))
                .modelName(MODEL_NAME)
                .build();
    }

    /** Self-describes as {@value #MODEL_NAME}, so callers that record a model name never label stub output as a real model. */
    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return ChatRequestParameters.builder().modelName(MODEL_NAME).build();
    }

    /**
     * Emits exactly the strict block the classification prompt asks for, so it parses through the
     * real parser. The values vary with the request so the breakdowns spread across buckets instead
     * of collapsing into a single bar — a one-bucket demo would hide any grouping bug in the page.
     */
    private static String classification(String prompt) {
        int seed = Math.floorMod(prompt.hashCode(), Integer.MAX_VALUE);
        String driver = pick(DRIVERS, seed);
        String category = pick(CATEGORIES, seed / DRIVERS.size());
        String feature = pick(FEATURES, seed / (DRIVERS.size() * CATEGORIES.size()));

        return """
                Ticket: 0
                Primary Driver: %s
                Category: %s
                Platform Feature: %s
                Reason: Stubbed classification — the tenant raised a %s issue around %s. This text is \
                generated locally and describes no real thread.""".formatted(driver, category, feature, category.toLowerCase(java.util.Locale.ROOT), feature);
    }

    /**
     * A JSON answer that passes the documentation classification's checks: {@code correct} quoting the
     * start of the first page, {@code missing} naming it as the closest page, or
     * {@code not_applicable}, varying with the request so the page shows more than one bucket.
     */
    private static String documentationClassification(String prompt) {
        int seed = Math.floorMod(prompt.hashCode(), Integer.MAX_VALUE);
        Matcher page = PAGE.matcher(prompt);
        Matcher question = QUESTION.matcher(prompt);
        String asked = question.find() ? question.group(1).strip() : "";
        Map<String, Object> answer = new LinkedHashMap<>();
        String classification;
        List<String> paths = List.of();
        String quote = "";
        if (!page.find()) {
            classification = "missing";
        } else {
            String path = page.group(1);
            String opening =
                    page.group(2).strip().lines().findFirst().orElse("").strip();
            classification = switch (seed % 3) {
                case 0 -> opening.isEmpty() ? "missing" : "correct";
                case 1 -> "missing";
                default -> "not_applicable";
            };
            if (!classification.equals("not_applicable")) {
                paths = List.of(path);
            }
            if (classification.equals("correct")) {
                quote = opening.substring(0, Math.min(opening.length(), MAX_QUOTE_LENGTH));
            }
        }
        answer.put("classification", classification);
        answer.put("question", asked.isEmpty() ? "How does a tenant use this platform feature?" : asked);
        answer.put("tags", List.of("stub-classification", "platform-feature"));
        answer.put("answer_summary", "");
        answer.put("doc_paths", paths);
        answer.put("evidence_quote", quote);
        answer.put("rationale", "Stubbed classification. This text is generated locally and describes no real thread.");
        try {
            return JSON.writeValueAsString(answer);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("a map of strings always serialises", e);
        }
    }

    /**
     * The canned summary, citing the report's first tickets in the markup the summary prompt asks
     * for, so the page's evidence popovers can be exercised locally. Plain prose when the report has
     * no reasons to cite.
     */
    private static String summary(String prompt) {
        List<String> ids = REPORT_TICKET
                .matcher(prompt)
                .results()
                .map(match -> match.group(1))
                .limit(3)
                .toList();
        String first = ids.isEmpty() ? null : String.join(",", ids.subList(0, Math.min(2, ids.size())));
        String second = ids.isEmpty() ? null : ids.getLast();
        return """
                Demand over this window was dominated by tenants trying to get workloads through the \
                path to production, with most threads turning on %s rather than platform faults. The \
                recurring shape is a tenant who knows a capability exists but cannot tell which of \
                several similar-looking options applies to their case.

                A smaller cluster concerns connectivity and access: requests that only the platform team \
                is authorised to action, which arrive as questions but are really %s. These are cheap \
                individually and add up, and are the most obvious candidate for self-service.

                This text is generated by the stub LLM provider and describes no real tickets. Enable a \
                real provider to get an actual summary.""".formatted(cite("configuration details", first), cite("task requests", second));
    }

    private static String cite(String phrase, @Nullable String ids) {
        return ids == null ? phrase : "[[" + phrase + "|" + ids + "]]";
    }

    private static String pick(List<String> values, int seed) {
        return values.get(Math.floorMod(seed, values.size()));
    }

    private static String textOf(List<ChatMessage> messages) {
        StringBuilder text = new StringBuilder();
        for (ChatMessage message : messages) {
            if (message instanceof UserMessage userMessage && userMessage.hasSingleText()) {
                text.append(userMessage.singleText()).append('\n');
            }
        }
        return text.toString();
    }
}
