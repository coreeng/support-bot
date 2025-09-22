package com.coreeng.supportbot.testkit;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SlackTestKit {
    private final TestKit.RoledTestKit testKit;
    private final SlackWiremock slackWiremock;
    private final SupportBotSlackClient supportBotSlackClient;

    public SlackMessage postMessage(MessageTs ts, String message) {
        return supportBotSlackClient.notifyMessagePosted(MessageToPost.builder()
            .userId(testKit.userId())
            .teamId(testKit.teamId())
            .channelId(testKit.channelId())
            .message(message)
            .ts(ts)
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

    public void clickMessageButton(MessageButtonClick click) {
        click.preSetupMocks();
        supportBotSlackClient.notifyButtonClicked(RawButtonClick.builder()
            .teamId(testKit.teamId())
            .userId(testKit.userId())
            .triggerId(click.triggerId())
            .actionId(click.actionId())
            .privateMetadata(click.privateMetadata())
            .build());
    }

    public void submitView(ViewSubmission viewSubmission) {
        supportBotSlackClient.notifyViewSubmitted(RawViewSubmission.builder()
            .teamId(testKit.teamId())
            .userId(testKit.userId())
            .triggerId(viewSubmission.triggerId())
            .callbackId(viewSubmission.callbackId())
            .privateMetadata(viewSubmission.privateMetadata())
            .values(viewSubmission.values())
            .viewType(viewSubmission.viewType())
            .build());
    }

    public <T> T submitView(ViewSubmission viewSubmission, ViewSubmissionResponseReceiver<T> receiver) {
        String responseBody = supportBotSlackClient.notifyViewSubmittedAndReturnBody(RawViewSubmission.builder()
            .teamId(testKit.teamId())
            .userId(testKit.userId())
            .triggerId(viewSubmission.triggerId())
            .callbackId(viewSubmission.callbackId())
            .privateMetadata(viewSubmission.privateMetadata())
            .values(viewSubmission.values())
            .viewType(viewSubmission.viewType())
            .build());
        return receiver.parse(responseBody);
    }
}
