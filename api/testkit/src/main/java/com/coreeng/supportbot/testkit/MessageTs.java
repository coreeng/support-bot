package com.coreeng.supportbot.testkit;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.NonNull;

public record MessageTs(@NonNull Instant instant) {
    private static final AtomicLong COUNTER = new AtomicLong(0L);

    /**
     * Creates a new MessageTs with the current epoch second and a monotonically
     * increasing microsecond suffix derived from an atomic counter.
     * <p>The right part (after the dot) is a 6-digit zero-padded counter-value
     * modulo 1_000_000, so generated timestamps look like Slack ts strings and
     * are effectively unique in tests.</p>
     */
    public static MessageTs now() {
        long epochSecond = Instant.now().getEpochSecond();
        long suffixMicros = COUNTER.getAndIncrement() % 1_000_000L;
        Instant instant = Instant.ofEpochSecond(epochSecond, suffixMicros * 1_000L);
        return new MessageTs(instant);
    }

    /**
     * Creates a new MessageTs with the current epoch second and an explicit
     * counter-derived microsecond suffix.
     */
    public static MessageTs now(int counterValue) {
        long epochSecond = Instant.now().getEpochSecond();
        long suffixMicros = Math.floorMod(counterValue, 1_000_000);
        Instant instant = Instant.ofEpochSecond(epochSecond, suffixMicros * 1_000L);
        return new MessageTs(instant);
    }

    @JsonCreator
    public static MessageTs fromTsString(String ts) {
        String[] parts = ts.split("\\.", 2);
        long epochSecs = Long.parseLong(parts[0]);

        long micros = 0L;
        if (parts.length > 1 && !parts[1].isEmpty()) {
            String frac = parts[1];
            if (frac.length() > 6) {
                frac = frac.substring(0, 6);
            } else if (frac.length() < 6) {
                frac = String.format("%-6s", frac).replace(' ', '0');
            }
            micros = Long.parseLong(frac);
        }

        Instant instant = Instant.ofEpochSecond(epochSecs, micros * 1_000L);
        return new MessageTs(instant);
    }

    @JsonValue
    @Override
    public @NonNull String toString() {
        long epochSecs = instant.getEpochSecond();
        long micros = instant.getNano() / 1_000L;
        return epochSecs + "." + String.format("%06d", micros);
    }
}
