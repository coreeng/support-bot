package com.coreeng.supportbot.testkit;

import org.jspecify.annotations.NonNull;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class UserProfileToGet {
    @NonNull
    private final String userId;
    @NonNull
    private final String email;
}
