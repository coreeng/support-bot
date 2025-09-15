package com.coreeng.supportbot.testkit;

import java.time.Duration;

import static org.awaitility.Awaitility.await;
import org.jspecify.annotations.NonNull;

import com.coreeng.supportbot.wiremock.SlackWiremock;

import lombok.Builder;
import lombok.Getter;

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

    public StubWithResult<TicketMessage> expectThreadMessagePosted(ThreadMessagePostedExpectation<TicketMessage> expectation) {
        return slackWiremock.stubMessagePosted(expectation.toBuilder()
            .threadTs(ts)
            .channelId(channelId)
            .build());
    }

    public TicketCreationFlowStubs stubTicketCreationFlow(MessageTs newTicketMessageTs) {
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
