package com.coreeng.supportbot.slack.client;

import com.coreeng.supportbot.slack.MessageTs;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;

import org.jspecify.annotations.Nullable;

public record SlackPostMessageRequest(
    SlackMessage message,
    String channel,
    @Nullable
    MessageTs threadTs
) {
    public ChatPostMessageRequest toSlackRequest() {
        return ChatPostMessageRequest.builder()
            .text(message.getText())
            .blocks(message.renderBlocks())
            .attachments(message.renderAttachments())
            .channel(channel)
            .threadTs(threadTs != null ? threadTs.ts() : null)
            .build();
    }
}
