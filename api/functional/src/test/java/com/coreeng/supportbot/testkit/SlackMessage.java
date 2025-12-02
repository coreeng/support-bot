package com.coreeng.supportbot.testkit;

import java.time.Duration;

import static org.awaitility.Awaitility.await;

import org.jspecify.annotations.NonNull;

import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

@Builder
@Getter
public class SlackMessage {
    @NonNull
    private final SlackWiremock slackWiremock;

    @NonNull
    private final MessageTs ts;
    @NonNull
    private final String channelId;

    public Stub expectReactionAdded(String reaction) {
        return slackWiremock.stubReactionAdd(
            ReactionAddedExpectation.builder()
                .reaction(reaction)
                .channelId(channelId)
                .ts(ts)
                .build()
        );
    }

    /**
     * Stub conversations.replies to indicate this message is a thread reply.
     *
     * @param threadTs The parent thread timestamp (indicates this message is a reply in that thread)
     */
    public Stub stubAsThreadReply(MessageTs threadTs) {
        return slackWiremock.stubConversationsReplies(ConversationRepliesToGet.builder()
            .channelId(channelId)
            .ts(ts)
            .threadTs(threadTs)
            .reply(ts)
            .build());
    }

    public StubWithResult<TicketMessage> expectThreadMessagePosted(ThreadMessagePostedExpectation<TicketMessage> expectation) {
        return slackWiremock.stubMessagePosted(expectation.toBuilder()
            .threadTs(ts)
            .channelId(channelId)
            .build());
    }

    public TicketCreationFlowStubs stubTicketCreationFlow(MessageTs newTicketMessageTs) {
        return stubTicketCreationFlow(newTicketMessageTs, null);
    }

    public TicketCreationFlowStubs stubTicketCreationFlow(MessageTs newTicketMessageTs, @Nullable MessageTs replyTs) {
        // Stub conversations.replies to indicate this is NOT a thread reply
        // This is needed because the service checks if the message is a thread reply before creating a ticket
        slackWiremock.stubConversationsReplies(ConversationRepliesToGet.builder()
            .channelId(channelId)
            .ts(ts)
            .threadTs(ts)
            .reply(replyTs)
            .build());

        Stub reaction = expectReactionAdded("ticket");
        StubWithResult<TicketMessage> posted = expectThreadMessagePosted(
            ThreadMessagePostedExpectation.<TicketMessage>builder()
                .receiver(new TicketMessage.Receiver())
                .from(UserRole.supportBot)
                .newMessageTs(newTicketMessageTs)
                .build()
        );
        return new TicketCreationFlowStubs(reaction, posted);
    }

    public record TicketCreationFlowStubs(Stub reactionAdded, StubWithResult<TicketMessage> ticketMessagePosted) {
        public void awaitAllCalled(Duration timeout, String reason) {
            await().atMost(timeout).untilAsserted(() -> {
                reactionAdded.assertIsCalled(reason + ": reaction added");
                ticketMessagePosted.assertIsCalled(reason + ": ticket message posted");
            });
        }
    }
}
