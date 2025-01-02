package com.coreeng.supportbot.slack.client;

import com.coreeng.supportbot.slack.MessageTs;

public record SlackGetMessageByTsRequest(
    String channelId,
    MessageTs ts
) {
}
