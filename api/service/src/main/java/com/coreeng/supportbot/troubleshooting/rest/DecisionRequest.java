package com.coreeng.supportbot.troubleshooting.rest;

import org.jspecify.annotations.Nullable;

public record DecisionRequest(
        @Nullable String decision, @Nullable String text) {}
