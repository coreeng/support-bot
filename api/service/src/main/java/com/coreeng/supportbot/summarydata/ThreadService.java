package com.coreeng.supportbot.summarydata;

import com.coreeng.supportbot.slack.client.SlackClient;
import com.google.common.collect.ImmutableList;
import com.slack.api.methods.request.conversations.ConversationsHistoryRequest;
import com.slack.api.methods.request.conversations.ConversationsRepliesRequest;
import com.slack.api.methods.response.conversations.ConversationsHistoryResponse;
import com.slack.api.methods.response.conversations.ConversationsRepliesResponse;
import com.slack.api.model.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for fetching all messages from a Slack thread using the conversations.replies API.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ThreadService {

    private static final int pageLimit = 200; // Recommended page size for Slack API
    private static final String whiteCheckMarkEmoji = "white_check_mark";

    // Pattern to match Slack user/workspace mentions like <@U12345678> or @W12345678 or U12345678
    private static final Pattern slackMentionPattern = Pattern.compile("<?@?[UW][A-Z0-9]{8,}>?");

    private final SlackClient slackClient;

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
                    .limit(pageLimit);

            if (cursor != null) {
                requestBuilder.cursor(cursor);
            }

            ConversationsRepliesResponse response = slackClient.getThreadPage(requestBuilder.build());

            if (response.getMessages() != null) {
                allMessages.addAll(response.getMessages());
                log.debug("Fetched {} messages in page {}", response.getMessages().size(), pageCount);
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

        log.info("Fetched total of {} messages from thread in {} pages: channel={}, threadTs={}",
                 allMessages.size(), pageCount, channelId, threadTs);

        // Extract text from all messages, filtering out null/empty texts
        ImmutableList<String> messageTexts = allMessages.stream()
            .map(Message::getText)
            .filter(text -> text != null && !text.isEmpty())
            .collect(ImmutableList.toImmutableList());

        log.debug("Extracted {} non-empty message texts", messageTexts.size());

        return messageTexts;
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

        // Remove Slack mentions from each message and join with double newlines
        String result = messageTexts.stream()
            .map(text -> slackMentionPattern.matcher(text).replaceAll(""))
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
                    .limit(pageLimit);

            if (cursor != null) {
                requestBuilder.cursor(cursor);
            }

            ConversationsHistoryResponse response = slackClient.getHistoryPage(requestBuilder.build());

            if (response.getMessages() != null) {
                log.debug("Processing {} messages from page {}", response.getMessages().size(), pageCount);

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

        return message.getReactions().stream()
            .anyMatch(reaction -> whiteCheckMarkEmoji.equals(reaction.getName()));
    }
}

