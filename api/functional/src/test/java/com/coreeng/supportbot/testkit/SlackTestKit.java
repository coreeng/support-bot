package com.coreeng.supportbot.testkit;

import io.restassured.response.ValidatableResponse;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SlackTestKit {
    private final TestKit.RoledTestKit testKit;
    private final SlackWiremock slackWiremock;
    private final SupportBotSlackClient supportBotSlackClient;

    public SlackMessage postMessage(MessageTs ts, String message) {
        return supportBotSlackClient.notifyMessagePosted(MessageToPost.builder()
            .userId(testKit.userId())
            .botId(testKit.botId())
            .teamId(testKit.teamId())
            .channelId(testKit.channelId())
            .message(message)
            .ts(ts)
            .build());
    }

    /**
     * Post a reply in a thread.
     *
     * @param ts       Timestamp of the reply message
     * @param threadTs Timestamp of the parent thread (original message)
     * @param message  The message content
     * @return The posted SlackMessage representing the reply
     */
    public SlackMessage postThreadReply(MessageTs ts, MessageTs threadTs, String message) {
        return supportBotSlackClient.notifyMessagePosted(MessageToPost.builder()
            .userId(testKit.userId())
            .botId(testKit.botId())
            .teamId(testKit.teamId())
            .channelId(testKit.channelId())
            .message(message)
            .ts(ts)
            .threadTs(threadTs)
            .build());
    }

    /**
     * Delete a message (top-level message, not a thread reply).
     *
     * @param message The message to delete
     */
    public void deleteMessage(SlackMessage message) {
        supportBotSlackClient.notifyMessageDeleted(MessageToDelete.builder()
            .userId(testKit.userId())
            .teamId(testKit.teamId())
            .channelId(message.channelId())
            .deletedTs(message.ts())
            .build());
    }

    /**
     * Delete a thread reply message.
     *
     * @param message  The thread reply message to delete
     * @param threadTs Timestamp of the parent thread (original query message)
     */
    public void deleteThreadReply(SlackMessage message, MessageTs threadTs) {
        supportBotSlackClient.notifyMessageDeleted(MessageToDelete.builder()
            .userId(testKit.userId())
            .teamId(testKit.teamId())
            .channelId(message.channelId())
            .deletedTs(message.ts())
            .threadTs(threadTs)
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

    public ValidatableResponse requestBlockSuggestion(BlockSuggestionRequest request) {
        return supportBotSlackClient.notifyBlockSuggestion(RawBlockSuggestion.builder()
            .teamId(testKit.teamId())
            .userId(testKit.userId())
            .actionId(request.actionId())
            .value(request.value())
            .viewType(request.viewType())
            .privateMetadata(request.privateMetadata())
            .callbackId(request.callbackId())
            .build());
    }
}
