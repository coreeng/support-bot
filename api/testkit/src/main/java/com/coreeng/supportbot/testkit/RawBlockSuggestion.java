package com.coreeng.supportbot.testkit;

import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.NonNull;

@Builder
@Getter
public class RawBlockSuggestion {
    @NonNull private final String teamId;

    @NonNull private final String userId;

    @NonNull private final String actionId;

    @NonNull private final String value;

    @NonNull private final String viewType;

    @NonNull private final String privateMetadata;

    @NonNull private final String callbackId;
}
