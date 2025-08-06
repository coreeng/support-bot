package com.coreeng.supportbot.testkit;

import com.coreeng.supportbot.wiremock.SlackWiremock;
import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.NonNull;

@Builder
@Getter
public class SlackMessage {
    @NonNull
    private final SlackWiremock slackWiremock;

    @NonNull
    private final String ts;
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

    public static String generateNewTs() {
        return System.currentTimeMillis() + "." + System.currentTimeMillis();
    }
}
