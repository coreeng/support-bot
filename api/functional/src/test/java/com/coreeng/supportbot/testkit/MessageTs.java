package com.coreeng.supportbot.testkit;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.jspecify.annotations.NonNull;

import java.time.Instant;

public record MessageTs(
    @NonNull
    Instant instant
) {
    public static MessageTs now() {
        return new MessageTs(Instant.now());
    }

    @JsonCreator
    public static MessageTs fromTsString(String ts) {
        String[] parts = ts.split("\\.");
        long epochSecs = Long.parseLong(parts[0]);
        long epochNano = Long.parseLong(parts[1]);
        return new MessageTs(Instant.ofEpochSecond(epochSecs, epochNano));

    }

    @JsonValue
    @Override
    public @NonNull String toString() {
        return instant.getEpochSecond() + "." + instant.getNano();
    }
}
