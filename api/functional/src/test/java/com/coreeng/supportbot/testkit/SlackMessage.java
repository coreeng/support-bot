package com.coreeng.supportbot.testkit;

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
}
