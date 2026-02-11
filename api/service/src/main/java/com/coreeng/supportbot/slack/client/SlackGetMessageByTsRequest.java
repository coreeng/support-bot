package com.coreeng.supportbot.slack.client;

import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.MessageTs;

public record SlackGetMessageByTsRequest(String channelId, MessageTs ts) {
    public static SlackGetMessageByTsRequest of(MessageRef messageRef) {
        return new SlackGetMessageByTsRequest(messageRef.channelId(), messageRef.ts());
    }
}
