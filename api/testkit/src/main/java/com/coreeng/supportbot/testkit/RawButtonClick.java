package com.coreeng.supportbot.testkit;

import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.NonNull;

@Builder
@Getter
public class RawButtonClick {
    @NonNull private final String teamId;

    @NonNull private final String userId;

    @NonNull private final String actionId;

    @NonNull private final String triggerId;

    @NonNull private final String privateMetadata;
}
