package com.coreeng.supportbot.testkit;

import lombok.Builder;

@Builder
public record MessageToDelete(
    String userId,
    String teamId,
    String channelId,
    MessageTs deletedTs,
    MessageTs threadTs
) {
}

