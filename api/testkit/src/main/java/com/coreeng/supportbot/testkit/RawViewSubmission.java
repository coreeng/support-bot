package com.coreeng.supportbot.testkit;

import com.google.common.collect.ImmutableList;
import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.NonNull;

@Builder
@Getter
public class RawViewSubmission {
    @NonNull private final String teamId;

    @NonNull private final String userId;

    @NonNull private final String triggerId;

    @NonNull private final String callbackId;

    @NonNull private final String privateMetadata;

    @NonNull private final ImmutableList<ViewSubmission.Value> values;

    @NonNull private final String viewType;
}
