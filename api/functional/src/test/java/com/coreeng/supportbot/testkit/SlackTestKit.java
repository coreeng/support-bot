package com.coreeng.supportbot.testkit;

import com.coreeng.supportbot.wiremock.SlackWiremock;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SlackTestKit {
    private final TestKit.RoledTestKit testKit;
    private final SlackWiremock slackWiremock;
    private final SupportBotSlackClient supportBotSlackClient;

    public SlackMessage postMessage(String message) {
        slackWiremock.stubAuthTest();
        return supportBotSlackClient.notifyMessagePosted(MessageToPost.builder()
            .userId(testKit.userId())
            .teamId(testKit.teamId())
            .channelId(testKit.channelId())
            .message(message)
            .build());
    }

    public void addReactionTo(SlackMessage targetMessage, String reaction) {
        slackWiremock.stubAuthTest();
        supportBotSlackClient.notifyReactionAdded(ReactionToAdd.builder()
            .userId(testKit.userId())
            .teamId(testKit.teamId())
            .channelId(targetMessage.channelId())
            .ts(targetMessage.ts())
            .reaction(reaction)
            .build());
    }
}
