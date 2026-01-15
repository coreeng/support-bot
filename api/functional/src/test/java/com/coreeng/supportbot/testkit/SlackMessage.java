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

    public Stub expectReactionAdded(String description, String reaction) {
        return slackWiremock.stubReactionAdd(
            ReactionAddedExpectation.builder()
                .description(description)
                .reaction(reaction)
                .channelId(channelId)
                .ts(ts)
                .build()
        );
    }

    /**
     * Stub conversations.replies to indicate this message is a thread reply.
     *
     * @param description Description for the stub
     * @param threadTs The parent thread timestamp (indicates this message is a reply in that thread)
     */
    public Stub stubAsThreadReply(String description, MessageTs threadTs) {
        return slackWiremock.stubConversationsReplies(ConversationRepliesToGet.builder()
            .description(description)
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

    public TicketCreationFlowStubs stubTicketCreationFlow(String reason, MessageTs newTicketMessageTs) {
        return stubTicketCreationFlow(reason, newTicketMessageTs, null);
    }

    public TicketCreationFlowStubs stubTicketCreationFlow(String reason, MessageTs newTicketMessageTs, @Nullable MessageTs replyTs) {
        // Stub conversations.replies to indicate this is NOT a thread reply
        // This is needed because the service checks if the message is a thread reply before creating a ticket
        Stub conversationsReplies = slackWiremock.stubConversationsReplies(ConversationRepliesToGet.builder()
            .description(reason + ": conversations.replies")
            .channelId(channelId)
            .ts(ts)
            .threadTs(ts)
            .reply(replyTs)
            .build());

        Stub reaction = expectReactionAdded(reason + ": reaction added", "ticket");
        StubWithResult<TicketMessage> posted = expectThreadMessagePosted(
            ThreadMessagePostedExpectation.<TicketMessage>builder()
                .description(reason + ": ticket message posted")
                .receiver(new TicketMessage.Receiver())
                .from(UserRole.supportBot)
                .newMessageTs(newTicketMessageTs)
                .build()
        );
        return new TicketCreationFlowStubs(conversationsReplies, reaction, posted);
    }

    public record TicketCreationFlowStubs(
        Stub conversationsReplies,
        Stub reactionAdded,
        StubWithResult<TicketMessage> ticketMessagePosted
    ) {
        public void awaitAllCalled(Duration timeout) {
            await().atMost(timeout).untilAsserted(() -> {
                conversationsReplies.assertIsCalled();
                reactionAdded.assertIsCalled();
                ticketMessagePosted.assertIsCalled();
            });
        }

        public void assertNotCalled() {
            conversationsReplies.assertIsNotCalled();
            reactionAdded.assertIsNotCalled();
            ticketMessagePosted.assertIsNotCalled();
        }

        public void cleanUp() {
            conversationsReplies.cleanUp();
            reactionAdded.cleanUp();
            ticketMessagePosted.cleanUp();
        }
    }
}
