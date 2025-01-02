package com.coreeng.supportbot.slack;

import javax.annotation.Nullable;
import java.time.Instant;

import static com.google.common.base.Preconditions.checkNotNull;

public record MessageTs(
    String ts
) {
    public MessageTs {
        checkNotNull(ts);
    }

    public static MessageTs of(String ts) {
        return new MessageTs(ts);
    }

    public static MessageTs ofOrNull(@Nullable String ts) {
        return ts != null ? of(ts) : null;
    }

    public Instant getDate() {
        String[] parts = ts.split("\\.", 2);
        long tsInt = Long.parseLong(parts[0]);
        return Instant.ofEpochSecond(tsInt);
    }
}
