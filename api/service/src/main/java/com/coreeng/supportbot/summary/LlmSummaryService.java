package com.coreeng.supportbot.summary;

import com.coreeng.supportbot.config.AnalysisProps;
import com.google.common.collect.ImmutableList;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Turns a window's aggregated counts and per-ticket reasons into prose, via the same LangChain4j
 * {@link ChatModel} the per-ticket classifier uses.
 *
 * <p>The report is assembled here rather than in the prompt so the prompt text (which lives in a
 * migration) can be rewritten without changing what data it is given.
 */
@Service
@ConditionalOnProperty(name = "summary.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class LlmSummaryService {

    private final ChatModel chatModel;
    private final AnalysisProps analysisProps;

    /** The model name recorded alongside a generated summary, for traceability. */
    public String modelName() {
        return analysisProps.llm().modelName();
    }

    /**
     * @param prompt the in-use summary prompt text
     * @param breakdowns the window's aggregated counts
     * @param reasons per-ticket {@code Reason} lines for the window
     * @return the generated prose
     */
    public String generate(String prompt, SummaryBreakdowns breakdowns, ImmutableList<String> reasons) {
        String report = buildReport(breakdowns, reasons);
        log.info(
                "Generating summary for window {}..{} ({} tickets, {} reasons)",
                breakdowns.window().from(),
                breakdowns.window().to(),
                breakdowns.totalTickets(),
                reasons.size());
        return chatModel.chat(prompt + "\n\n--- BEGIN WINDOW REPORT ---\n" + report + "\n--- END WINDOW REPORT ---\n");
    }

    private static String buildReport(SummaryBreakdowns breakdowns, ImmutableList<String> reasons) {
        StringBuilder report = new StringBuilder(1024);
        report.append("Window: ")
                .append(breakdowns.window().from())
                .append(" to ")
                .append(breakdowns.window().to())
                .append(" (inclusive)\n")
                .append("Tickets raised in the window: ")
                .append(breakdowns.totalTickets())
                .append('\n')
                .append("Classified tickets: ")
                .append(breakdowns.classifiedTickets())
                .append('\n')
                .append("Still open or not yet classified: ")
                .append(breakdowns.unclassifiedTickets())
                .append('\n');

        appendCounts(report, "Primary support drivers", breakdowns.drivers());
        appendCounts(report, "Categories", breakdowns.categories());
        appendCounts(report, "Knowledge gaps by category", breakdowns.knowledgeGaps());
        appendCounts(report, "Platform features", breakdowns.features());
        appendCounts(report, "Tenant teams", breakdowns.teams());

        report.append("\nPer-ticket reasons (").append(reasons.size()).append("):\n");
        for (String reason : reasons) {
            report.append("- ").append(reason).append('\n');
        }
        return report.toString();
    }

    private static void appendCounts(StringBuilder report, String title, ImmutableList<SummaryCount> counts) {
        report.append('\n').append(title).append(":\n");
        if (counts.isEmpty()) {
            report.append("- (none)\n");
            return;
        }
        for (SummaryCount count : counts) {
            report.append("- ")
                    .append(count.label())
                    .append(": ")
                    .append(count.count())
                    .append('\n');
        }
    }
}
