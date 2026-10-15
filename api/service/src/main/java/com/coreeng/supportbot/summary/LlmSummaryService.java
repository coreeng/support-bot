package com.coreeng.supportbot.summary;

import com.coreeng.supportbot.config.ConditionalOnLlmEnabled;
import com.coreeng.supportbot.config.LlmProps;
import com.coreeng.supportbot.teamgroups.TeamGroup;
import com.coreeng.supportbot.teamgroups.TeamGroupService;
import com.google.common.collect.ImmutableList;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Turns a window's aggregated counts and per-ticket reasons into prose, via the same LangChain4j
 * {@link ChatModel} the per-ticket classifier uses.
 *
 * <p>The report is assembled here rather than in the prompt so the prompt text (which lives in a
 * migration) can be rewritten without changing what data it is given.
 */
@Service
@ConditionalOnLlmEnabled
@RequiredArgsConstructor
@Slf4j
public class LlmSummaryService {

    private final ChatModel chatModel;
    private final LlmProps llmProps;
    private final TeamGroupService teamGroupService;

    /**
     * The model name recorded alongside a generated summary, for traceability.
     *
     * <p>Prefers the name the {@link ChatModel} reports about itself over the configured
     * {@code llm.model-name}, so a provider that is not the configured model — the stub in
     * particular — is stamped as what it is rather than as e.g. {@code gemini-2.5-flash}. Falls back
     * to the configured name for providers that do not self-describe.
     */
    public String modelName() {
        String reported = chatModel.defaultRequestParameters().modelName();
        if (reported != null && !reported.isBlank()) {
            return reported;
        }
        return llmProps.modelName();
    }

    /**
     * @param prompt the in-use summary prompt text
     * @param scope what the summary covers; a team group adds a line saying so, so the model does
     *     not describe the group's tickets as the whole platform's
     * @param breakdowns the scope's aggregated counts
     * @param reasons per-ticket {@code Reason}s for the scope, each dated
     * @return the generated prose
     */
    public String generate(
            String prompt, SummaryScope scope, SummaryBreakdowns breakdowns, ImmutableList<SummaryReason> reasons) {
        String report = buildReport(scope.teamGroup(), breakdowns, reasons);
        log.info(
                "Generating summary for {} ({} tickets, {} reasons)",
                scope.describe(),
                breakdowns.totalTickets(),
                reasons.size());
        return chatModel.chat(prompt + "\n\n--- BEGIN WINDOW REPORT ---\n" + report + "\n--- END WINDOW REPORT ---\n");
    }

    private String buildReport(
            @Nullable TeamGroup teamGroup, SummaryBreakdowns breakdowns, ImmutableList<SummaryReason> reasons) {
        StringBuilder report = new StringBuilder(1024);
        report.append("Window: ")
                .append(breakdowns.window().from())
                .append(" to ")
                .append(breakdowns.window().to())
                .append(" (inclusive)\n");
        // Only for a team group, so the whole window's report stays exactly as it was before groups
        // existed.
        if (teamGroup != null) {
            report.append("Scope: ")
                    .append(teamGroupService.displayName())
                    .append(" \"")
                    .append(teamGroup.label())
                    .append("\", which covers only tickets raised by these tenant teams: ")
                    .append(String.join(", ", teamGroup.teamCodes()))
                    .append(". Every count and reason below is limited to these teams.\n");
        }
        report.append("Tickets raised in the window: ")
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
        appendCounts(report, "Products (from product tags)", breakdowns.products());

        report.append("\nPer-ticket reasons (").append(reasons.size()).append("):\n");
        for (SummaryReason reason : reasons) {
            report.append("- ").append(reason.line()).append('\n');
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
            report.append("- ").append(count.label()).append(": ").append(count.count());
            if (count.topProduct() != null) {
                report.append(" (top product: ").append(count.topProduct()).append(')');
            }
            report.append('\n');
        }
    }
}
