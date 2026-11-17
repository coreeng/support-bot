package com.coreeng.supportbot.troubleshooting;

import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.troubleshooting.Payloads.SlackMessage;
import com.slack.api.methods.request.conversations.ConversationsRepliesRequest;
import com.slack.api.model.Message;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Copies the query's Slack thread into the session once, at start, so the router and the cards
 * read the same messages. Read-only. Known shortcut: the text skips the thread sanitisation the
 * analysis applies, which is acceptable for a test workspace only.
 */
@Component
@ConditionalOnTroubleshootingEnabled
@RequiredArgsConstructor
@Slf4j
public class SlackThreadSnapshotter {
    private static final int MAX_MESSAGES = 200;

    private final SlackClient slackClient;
    private final TroubleshootingSessionService sessions;

    public void snapshot(long sessionId, Ticket ticket) {
        List<Message> messages;
        try {
            messages = slackClient
                    .getThreadPage(ConversationsRepliesRequest.builder()
                            .channel(ticket.channelId())
                            .ts(ticket.queryTs().ts())
                            .limit(MAX_MESSAGES)
                            .build())
                    .getMessages();
        } catch (RuntimeException e) {
            log.atInfo()
                    .addArgument(ticket::id)
                    .addArgument(e::getMessage)
                    .log("Slack thread for ticket {} is not available: {}");
            messages = null;
        }
        if (messages == null || messages.isEmpty()) {
            sessions.append(
                    sessionId,
                    TroubleshootingEventType.SLACK_MESSAGE,
                    Actors.SYSTEM,
                    new SlackMessage(null, null, "The query's Slack thread could not be read.", false));
            return;
        }
        for (Message message : messages) {
            String userId = message.getUser() != null
                    ? message.getUser()
                    : message.getBotId() != null ? "bot:" + message.getBotId() : "unknown";
            sessions.append(
                    sessionId,
                    TroubleshootingEventType.SLACK_MESSAGE,
                    Actors.slack(userId),
                    new SlackMessage(
                            message.getTs(), userId, message.getText() == null ? "" : message.getText(), true));
        }
    }
}
