package com.coreeng.supportbot.slack;

import com.slack.api.app_backend.interactive_components.payload.BlockActionPayload;

public record MessageRef(
    MessageTs ts,
    MessageTs threadTs,
    String channelId
) {
    public static MessageRef from(BlockActionPayload payload) {
        return new MessageRef(
            MessageTs.of(payload.getMessage().getTs()),
            MessageTs.ofOrNull(payload.getMessage().getThreadTs()),
            payload.getChannel().getId()
        );
    }

    public MessageTs actualThreadTs() {
        return threadTs != null ? threadTs : ts;
    }

    public boolean isReply() {
        return threadTs != null;
    }
}
