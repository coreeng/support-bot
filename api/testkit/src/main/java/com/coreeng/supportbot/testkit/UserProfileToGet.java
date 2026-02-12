package com.coreeng.supportbot.testkit;

import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.NonNull;

@Builder
@Getter
public class UserProfileToGet {
    @NonNull private final String description;

    @NonNull private final String userId;

    @NonNull private final String email;
}
