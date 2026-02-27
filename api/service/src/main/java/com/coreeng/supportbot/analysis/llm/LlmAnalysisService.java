package com.coreeng.supportbot.analysis.llm;

import com.coreeng.supportbot.analysis.AnalysisRepository.AnalysisRecord;
import com.coreeng.supportbot.summarydata.ThreadService;
import com.google.common.base.Splitter;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Service for analyzing Slack support threads using an LLM (Large Language Model).
 *
 * <p>This service:
 * <ul>
 *   <li>Fetches thread content from Slack via {@link ThreadService}</li>
 *   <li>Combines the thread with a prompt template</li>
 *   <li>Calls the LLM (configured via {@link dev.langchain4j.model.chat.ChatLanguageModel})</li>
 *   <li>Parses the LLM response into structured {@link AnalysisRecord} data</li>
 * </ul>
 *
 * <p>The LLM is expected to return a multiline response in the format:
 * <pre>
 * Ticket: 12345
 * Primary Driver: Knowledge Gap
 * Category: Monitoring & Troubleshooting Tenant Applications
 * Platform Feature: workload compute
 * Reason: Tenant's E2E test jobs were failing due to...
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LlmAnalysisService {

    private final ChatLanguageModel chatLanguageModel;
    private final ThreadService threadService;

    /**
     * Analyzes a single Slack thread using the LLM.
     *
     * @param channelId Slack channel ID
     * @param threadTs Slack thread timestamp
     * @param ticketId Ticket ID to include in the analysis record
     * @param prompt The prompt text to send to the LLM (loaded from file)
     * @return Analysis record with extracted fields, or null if analysis fails
     */
    public @Nullable AnalysisRecord analyzeThread(String channelId, String threadTs, Long ticketId, String prompt) {
        try {
            // Fetch thread text from Slack
            String threadText = threadService.getThreadAsText(channelId, threadTs);

            // Combine thread with the prompt
            String threadWithPrompt = buildPrompt(threadText, prompt);

            // Call LLM
            log.debug("Calling LLM for thread {}", threadTs);
            String response = chatLanguageModel.generate(threadWithPrompt);

            // Parse response into structured data
            return parseResponse(response, ticketId);
        } catch (Exception e) {
            log.error("Failed to analyze thread {}: {}", threadTs, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Builds the full prompt by combining the thread content with the prompt template.
     *
     * @param threadText The Slack thread content
     * @param prompt The prompt template
     * @return The complete prompt to send to the LLM
     */
    private String buildPrompt(String threadText, String prompt) {
        return String.format("""
I have a Slack thread with the following content:

--- BEGIN SLACK THREAD ---
%s
--- END SLACK THREAD ---

%s
""", threadText, prompt);
    }

    /**
     * Parses the LLM response into an {@link AnalysisRecord}.
     *
     * <p>Expected format:
     * <pre>
     * Ticket: 12345
     * Primary Driver: Knowledge Gap
     * Category: Monitoring & Troubleshooting
     * Platform Feature: workload compute
     * Reason: Summary text...
     * </pre>
     *
     * @param response The raw LLM response
     * @param ticketId The ticket ID to use (ignores the "Ticket:" line from LLM)
     * @return Parsed analysis record, or null if parsing fails
     */
    private @Nullable AnalysisRecord parseResponse(String response, Long ticketId) {
        try {
            String driver = null;
            String category = null;
            String feature = null;
            String summary = null;

            Iterable<String> lines = Splitter.onPattern("\\r?\\n").split(response);
            for (String line : lines) {
                if (line.startsWith("Ticket:")) {
                    // Ignore - we use the ticketId parameter
                } else if (line.startsWith("Primary Driver:")) {
                    driver = line.replace("Primary Driver:", "").trim();
                } else if (line.startsWith("Category:")) {
                    category = line.replace("Category:", "").trim();
                } else if (line.startsWith("Platform Feature:")) {
                    feature = line.replace("Platform Feature:", "").trim();
                } else if (line.startsWith("Reason:")) {
                    summary = line.replace("Reason:", "").trim();
                }
            }

            // Note: promptId is set to null here; it's added by the caller (AnalysisService)
            return new AnalysisRecord(ticketId.intValue(), driver, category, feature, summary, null);
        } catch (Exception e) {
            log.error("Failed to parse LLM response: {}", e.getMessage());
            return null;
        }
    }
}
