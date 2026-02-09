package com.coreeng.supportbot.slack;

import com.fasterxml.jackson.annotation.JsonValue;

import org.jspecify.annotations.Nullable;
import java.time.Instant;

import static com.google.common.base.Preconditions.checkNotNull;

public record MessageTs(
    @JsonValue String ts,
    // TODO: delete me when no more mocking data is required
    boolean mocked
) {
    private static final String mockedPrefix = "MOCKED_";

    public MessageTs {
        checkNotNull(ts);
    }

    public MessageTs(String ts) {
        this(ts.replaceFirst("^" + mockedPrefix, ""), ts.startsWith(mockedPrefix));
    }

    public static MessageTs mocked(String ts) {
        return new MessageTs(ts, true);
    }

    public static MessageTs of(String ts) {
        return new MessageTs(ts);
    }

    @Nullable
    public static MessageTs ofOrNull(@Nullable String ts) {
        return ts != null ? of(ts) : null;
    }

    @Override
    public String ts() {
        if (mocked) {
            return mockedPrefix + ts;
        }
        return ts;
    }

    public Instant getDate() {
        String[] parts = ts.split("\\.", 2);
        long tsInt = Long.parseLong(parts[0]);
        return Instant.ofEpochSecond(tsInt);
    }
}
