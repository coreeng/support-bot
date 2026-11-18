package com.coreeng.supportbot.troubleshooting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.coreeng.supportbot.troubleshooting.Payloads.SlackMessage;
import com.slack.api.methods.request.conversations.ConversationsRepliesRequest;
import com.slack.api.methods.response.conversations.ConversationsRepliesResponse;
import com.slack.api.model.Message;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class SlackThreadSnapshotterTest {
    private static final long SESSION = 3;
    private static final Ticket TICKET = Ticket.builder()
            .id(new TicketId(7))
            .channelId("C1")
            .queryTs(MessageTs.of("100.1"))
            .status(TicketStatus.opened)
            .build();

    private final SlackClient slack = mock(SlackClient.class);
    private final TroubleshootingSessionService sessions = mock(TroubleshootingSessionService.class);
    private final SlackThreadSnapshotter snapshotter = new SlackThreadSnapshotter(slack, sessions);

    @Test
    void storesOneEventPerThreadMessageWithTheAuthorAsActor() {
        ConversationsRepliesResponse response = new ConversationsRepliesResponse();
        response.setMessages(List.of(
                message("100.1", "U1", null, "Pods are pending"), message("100.2", null, "B1", "Ticket created")));
        when(slack.getThreadPage(any())).thenReturn(response);

        snapshotter.snapshot(SESSION, TICKET);

        ArgumentCaptor<ConversationsRepliesRequest> request =
                ArgumentCaptor.forClass(ConversationsRepliesRequest.class);
        verify(slack).getThreadPage(request.capture());
        assertThat(request.getValue().getChannel()).isEqualTo("C1");
        assertThat(request.getValue().getTs()).isEqualTo("100.1");
        InOrder order = inOrder(sessions);
        order.verify(sessions)
                .append(
                        SESSION,
                        TroubleshootingEventType.SLACK_MESSAGE,
                        "slack:U1",
                        new SlackMessage("100.1", "Pods are pending", true));
        order.verify(sessions)
                .append(
                        SESSION,
                        TroubleshootingEventType.SLACK_MESSAGE,
                        "slack:bot:B1",
                        new SlackMessage("100.2", "Ticket created", true));
    }

    @Test
    void recordsThatTheThreadIsNotAvailableWhenSlackFails() {
        when(slack.getThreadPage(any())).thenThrow(new IllegalStateException("thread_not_found"));

        snapshotter.snapshot(SESSION, TICKET);

        verify(sessions)
                .append(
                        eq(SESSION),
                        eq(TroubleshootingEventType.SLACK_MESSAGE),
                        eq(Actors.SYSTEM),
                        eq(new SlackMessage(null, "The query's Slack thread could not be read.", false)));
    }

    private static Message message(String ts, @Nullable String user, @Nullable String botId, String text) {
        Message message = new Message();
        message.setTs(ts);
        message.setUser(user);
        message.setBotId(botId);
        message.setText(text);
        return message;
    }
}
