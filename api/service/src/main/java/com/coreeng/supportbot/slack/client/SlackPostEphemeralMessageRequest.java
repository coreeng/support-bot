package com.coreeng.supportbot.slack.client;

import com.coreeng.supportbot.slack.MessageTs;
import com.slack.api.methods.request.chat.ChatPostEphemeralRequest;

import org.jspecify.annotations.Nullable;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
public class SlackPostEphemeralMessageRequest {
   private final SlackMessage message;
   private final String channel;
   private final String userId;
   @Nullable
   private final MessageTs threadTs;

    public ChatPostEphemeralRequest toSlackRequest() {
        return ChatPostEphemeralRequest.builder()
            .text(message.getText())
            .blocks(message.renderBlocks())
            .attachments(message.renderAttachments())
            .channel(channel)
            .user(userId)
            .threadTs(threadTs != null ? threadTs.ts() : null)
            .build();
    }
}