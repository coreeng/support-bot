package com.coreeng.supportbot.summarydata;

import com.coreeng.supportbot.slack.client.SlackClient;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.slack.api.methods.request.conversations.ConversationsHistoryRequest;
import com.slack.api.methods.request.conversations.ConversationsRepliesRequest;
import com.slack.api.methods.response.conversations.ConversationsHistoryResponse;
import com.slack.api.methods.response.conversations.ConversationsRepliesResponse;
import com.slack.api.model.Message;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * Service for fetching all messages from a Slack thread using the conversations.replies API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ThreadService {

    private static final int PAGE_LIMIT = 200; // Recommended page size for Slack API
    private static final String WHITE_CHECK_MARK_EMOJI = "white_check_mark";

    // Pattern to match Slack user/workspace mentions like <@U12345678> or @W12345678 or U12345678
    private static final Pattern SLACK_MENTION_PATTERN = Pattern.compile("<?@?[UW][A-Z0-9]{8,}>?");

    // Pattern to match capitalized words that might be names
    private static final Pattern NAME_PATTERN = Pattern.compile("\\b([A-Z][a-z]+(?:\\s+[A-Z][a-z]+)*)\\b");

    // Common words that are capitalized but not names (to avoid false positives)
    // Loaded from common-words.txt resource file at startup
    private static final Set<String> COMMON_WORDS = loadCommonWords();

    private final SlackClient slackClient;

    /**
     * Loads common words from the common-words.txt resource file.
     * This method is called once at class initialization time.
     *
     * @return Set of common capitalized words that should not be treated as names
     */
    private static Set<String> loadCommonWords() {
        try {
            ClassPathResource resource = new ClassPathResource("commonly-capitalised-words.txt");
            Set<String> words = new HashSet<>();

            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        words.add(line);
                    }
                }
            }

            log.info("Loaded {} common words from common-words.txt", words.size());
            return Set.copyOf(words); // Return immutable set
        } catch (IOException e) {
            log.error("Failed to load common-words.txt, using empty set", e);
            return Set.of(); // Return empty set on error
        }
    }

    /**
     * Record containing thread timestamp and its text content.
     */
    public record ThreadData(String threadTs, String text) {}

    /**
     * Fetches all message texts from a Slack thread.
     *
     * @param channelId The Slack channel ID
     * @param threadTs The thread timestamp (ts of the parent message)
     * @return List of message texts from all messages in the thread
     */
    public ImmutableList<String> getAllThreadMessageTexts(String channelId, String threadTs) {
        log.debug("Fetching all messages from thread: channel={}, threadTs={}", channelId, threadTs);

        List<Message> allMessages = new ArrayList<>();
        String cursor = null;
        boolean hasMore = true;
        int pageCount = 0;

        while (hasMore) {
            pageCount++;
            log.debug("Fetching page {} for thread: channel={}, threadTs={}", pageCount, channelId, threadTs);

            ConversationsRepliesRequest.ConversationsRepliesRequestBuilder requestBuilder =
                    ConversationsRepliesRequest.builder()
                            .channel(channelId)
                            .ts(threadTs)
                            .limit(PAGE_LIMIT);

            if (cursor != null) {
                requestBuilder.cursor(cursor);
            }

            ConversationsRepliesResponse response = slackClient.getThreadPage(requestBuilder.build());

            if (response.getMessages() != null) {
                allMessages.addAll(response.getMessages());
                log.debug(
                        "Fetched {} messages in page {}", response.getMessages().size(), pageCount);
            }

            // Check if there are more pages
            hasMore = response.isHasMore();
            if (hasMore && response.getResponseMetadata() != null) {
                cursor = response.getResponseMetadata().getNextCursor();
                log.debug("More messages available, cursor: {}", cursor);
            } else {
                hasMore = false;
            }
        }

        log.info(
                "Fetched total of {} messages from thread in {} pages: channel={}, threadTs={}",
                allMessages.size(),
                pageCount,
                channelId,
                threadTs);

        // Extract text from all messages, filtering out null/empty texts
        ImmutableList<String> messageTexts = allMessages.stream()
                .map(Message::getText)
                .filter(text -> text != null && !text.isEmpty())
                .collect(ImmutableList.toImmutableList());

        log.debug("Extracted {} non-empty message texts", messageTexts.size());

        return messageTexts;
    }

    /**
     * Remove human names from text using NLP-based pattern matching.
     * Processes each line independently to preserve conversation structure.
     * Identifies and removes:
     * - Capitalized words that appear to be names (e.g., "Oleg", "John Smith")
     * - Common name patterns in sentences
     *
     * @param text Text containing potential human names
     * @return Text with names removed, preserving line delimiters
     */
    private String removeHumanNames(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // Process each line independently to preserve conversation structure
        Iterable<String> lines = Splitter.on('\n').split(text);
        List<String> processedLines = new ArrayList<>();

        for (String line : lines) {
            // Skip empty lines
            if (line.trim().isEmpty()) {
                processedLines.add(line);
                continue;
            }

            // Find capitalized words that might be names
            Matcher matcher = NAME_PATTERN.matcher(line);
            StringBuffer processedLine = new StringBuffer();

            while (matcher.find()) {
                String match = matcher.group(1);

                // Split multi-word names
                List<String> words = Splitter.onPattern("\\s+").splitToList(match);

                // Filter out common words
                List<String> filteredWords = words.stream()
                        .filter(word -> !COMMON_WORDS.contains(word))
                        .collect(Collectors.toList());

                // If all words were common words, keep the original
                if (filteredWords.isEmpty()) {
                    matcher.appendReplacement(processedLine, Matcher.quoteReplacement(match));
                    continue;
                }

                // Check if it's at start of line
                boolean isStartOfLine = line.trim().startsWith(match);

                // If it's a multi-word capitalized phrase (likely a name), remove it
                if (words.size() > 1) {
                    matcher.appendReplacement(processedLine, "");
                    continue;
                }

                // Single word: remove if not at start of line or if it's clearly a name
                if (!isStartOfLine || !filteredWords.isEmpty()) {
                    // Additional heuristic: if the word is short (2-15 chars) and capitalized, likely a name
                    if (match.length() >= 2 && match.length() <= 15 && match.matches("^[A-Z][a-z]+$")) {
                        matcher.appendReplacement(processedLine, "");
                        continue;
                    }
                }

                matcher.appendReplacement(processedLine, Matcher.quoteReplacement(match));
            }
            matcher.appendTail(processedLine);

            // Clean up extra whitespace within the line
            String cleanedLine = processedLine
                    .toString()
                    .replaceAll("\\s+", " ") // Collapse multiple spaces
                    .replaceAll("\\s+([.,!?;:])", "$1") // Remove space before punctuation
                    .trim();

            processedLines.add(cleanedLine);
        }

        // Join lines back with original line delimiters
        return String.join("\n", processedLines);
    }

    /**
     * Fetches all messages from a Slack thread, removes Slack user/workspace mentions,
     * and joins them into a single string with double newline separators.
     *
     * @param channelId The Slack channel ID
     * @param threadTs The thread timestamp (ts of the parent message)
     * @return A single string with all message texts joined by "\n\n", with mentions removed
     */
    public String getThreadAsText(String channelId, String threadTs) {
        log.debug("Fetching thread as text: channel={}, threadTs={}", channelId, threadTs);

        ImmutableList<String> messageTexts = getAllThreadMessageTexts(channelId, threadTs);

        // Remove Slack mentions and human names from each message, then join with double newlines
        String result = messageTexts.stream()
                .map(text -> SLACK_MENTION_PATTERN.matcher(text).replaceAll(""))
                .map(this::removeHumanNames)
                .collect(Collectors.joining("\n\n"));

        log.debug("Processed thread text, final length: {} characters", result.length());

        return result;
    }

    /**
     * Fetches threads from the last N days that have a white_check_mark emoji reaction.
     * Returns a list of thread timestamps that can be passed to getThreadAsText().
     *
     * @param channelId The Slack channel ID to search
     * @param days Number of days to look back
     * @return List of thread timestamps (ts) that have white_check_mark reactions
     */
    public ImmutableList<String> getThreadsWithCheckMark(String channelId, int days) {
        log.info("Fetching threads with white_check_mark from last {} days in channel {}", days, channelId);

        // Calculate timestamp for N days ago
        Instant cutoffTime = Instant.now().minus(days, ChronoUnit.DAYS);
        String oldestTimestamp = String.valueOf(cutoffTime.getEpochSecond());

        log.debug("Searching for messages since timestamp: {} ({})", oldestTimestamp, cutoffTime);

        List<String> threadTimestamps = new ArrayList<>();
        String cursor = null;
        boolean hasMore = true;
        int pageCount = 0;

        while (hasMore) {
            pageCount++;
            log.debug("Fetching history page {} for channel {}", pageCount, channelId);

            ConversationsHistoryRequest.ConversationsHistoryRequestBuilder requestBuilder =
                    ConversationsHistoryRequest.builder()
                            .channel(channelId)
                            .oldest(oldestTimestamp)
                            .limit(PAGE_LIMIT);

            if (cursor != null) {
                requestBuilder.cursor(cursor);
            }

            ConversationsHistoryResponse response = slackClient.getHistoryPage(requestBuilder.build());

            if (response.getMessages() != null) {
                log.debug(
                        "Processing {} messages from page {}",
                        response.getMessages().size(),
                        pageCount);

                for (Message message : response.getMessages()) {
                    // Check if message has white_check_mark reaction
                    if (hasWhiteCheckMarkReaction(message)) {
                        // Use thread_ts if it's a thread parent, otherwise use ts
                        String threadTs = message.getThreadTs() != null ? message.getThreadTs() : message.getTs();
                        threadTimestamps.add(threadTs);
                        log.debug("Found thread with white_check_mark: {}", threadTs);
                    }
                }
            }

            // Check if there are more pages
            hasMore = response.isHasMore();
            if (hasMore && response.getResponseMetadata() != null) {
                cursor = response.getResponseMetadata().getNextCursor();
                log.debug("More messages available, cursor: {}", cursor);
            } else {
                hasMore = false;
            }
        }

        log.info("Found {} threads with white_check_mark in {} pages", threadTimestamps.size(), pageCount);

        return ImmutableList.copyOf(threadTimestamps);
    }

    /**
     * Fetches threads from the last N days that have a white_check_mark emoji reaction
     * and returns both the thread timestamp and the thread text.
     *
     * @param channelId The Slack channel ID to search
     * @param days Number of days to look back
     * @return List of ThreadData records containing thread timestamps and their text content
     */
    public ImmutableList<ThreadData> getThreadsWithCheckMarkAsText(String channelId, int days) {
        log.info("Fetching threads with white_check_mark as text from last {} days in channel {}", days, channelId);

        ImmutableList<String> threadTimestamps = getThreadsWithCheckMark(channelId, days);

        log.debug("Processing {} threads to extract text", threadTimestamps.size());

        ImmutableList<ThreadData> threadDataList = threadTimestamps.stream()
                .map(threadTs -> {
                    log.debug("Fetching text for thread: {}", threadTs);
                    String text = getThreadAsText(channelId, threadTs);
                    return new ThreadData(threadTs, text);
                })
                .collect(ImmutableList.toImmutableList());

        log.info("Successfully processed {} threads with text", threadDataList.size());

        return threadDataList;
    }

    /**
     * Checks if a message has a white_check_mark reaction.
     */
    private boolean hasWhiteCheckMarkReaction(Message message) {
        if (message.getReactions() == null || message.getReactions().isEmpty()) {
            return false;
        }

        return message.getReactions().stream().anyMatch(reaction -> WHITE_CHECK_MARK_EMOJI.equals(reaction.getName()));
    }
}
