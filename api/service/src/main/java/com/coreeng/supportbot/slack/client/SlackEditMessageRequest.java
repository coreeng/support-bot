package com.coreeng.supportbot.slack.client;

import com.coreeng.supportbot.slack.MessageTs;
import com.slack.api.methods.request.chat.ChatUpdateRequest;

public record SlackEditMessageRequest(SlackMessage message, String channel, MessageTs messageTs) {
    public ChatUpdateRequest toSlackRequest() {
        return ChatUpdateRequest.builder()
                .text(message.getText())
                .parse("full")
                .blocks(message.renderBlocks())
                .attachments(message.renderAttachments())
                .channel(channel)
                .ts(messageTs.ts())
                .build();
    }
}
