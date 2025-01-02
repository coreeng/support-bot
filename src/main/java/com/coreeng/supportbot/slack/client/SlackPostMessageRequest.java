package com.coreeng.supportbot.slack.client;

import com.coreeng.supportbot.slack.MessageTs;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;

public record SlackPostMessageRequest(
    SlackMessage message,
    String channel,
    MessageTs threadTs
) {
    public ChatPostMessageRequest toSlackRequest() {
        return ChatPostMessageRequest.builder()
            .text(message.getText())
            .blocks(message.renderBlocks())
            .attachments(message.renderAttachments())
            .channel(channel)
            .threadTs(threadTs.ts())
            .build();
    }
}
