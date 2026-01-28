package com.coreeng.supportbot.testkit;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

public record MessageTs(
    @NonNull
    Instant instant
) {
    private static final AtomicInteger counter = new AtomicInteger(0);

    /**
     * Creates a new MessageTs with the current time plus an atomic counter
     * to ensure uniqueness even when called multiple times within the same millisecond.
     * The counter fills the last 3 digits of the nanosecond field (modulo 1000).
     */
    public static MessageTs now() {
        return now(counter.getAndIncrement() % 1000);
    }

    /**
     * Creates a new MessageTs with the current time plus an explicit counter value.
     */
    public static MessageTs now(int counterValue) {
        Instant now = Instant.now();
        // Clear the last 3 digits and add the counter value
        long nanos = (now.getNano() / 1000) * 1000 + (counterValue % 1000);
        return new MessageTs(Instant.ofEpochSecond(now.getEpochSecond(), nanos));
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
