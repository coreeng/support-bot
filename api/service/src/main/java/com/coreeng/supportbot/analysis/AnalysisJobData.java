package com.coreeng.supportbot.analysis;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * Encoding of the {@code async_job.data} payload for the shared {@link #JOB_ID analysis} job.
 *
 * <p>The payload is a small JSON document discriminated by {@code type}: {@code
 * {"type":"days","days":7}} is a days-based classification run, {@code
 * {"type":"window","from":"2026-01-01","to":"2026-01-14"}} a Support Summary refresh over an explicit
 * date range. Rows written before the JSON form existed hold a bare integer (the days), and are still
 * read so an in-flight run survives the upgrade.
 *
 * <p>The mapper is private to this class rather than the application's: the stored form must not
 * change because someone tunes the REST mapper.
 *
 * <p>Parsing is total: an unrecognised payload yields {@code null} rather than throwing, because the
 * one caller that reads it is the startup resume, and a row left behind by a different version of
 * the service must be cleaned up rather than crash the boot.
 */
public final class AnalysisJobData {

    /** The id of the one {@code async_job} row every analysis run — days-based or windowed — claims. */
    public static final String JOB_ID = "analysis";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            // A field added by a newer version must not make an older one delete the in-flight run...
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            // ...but a field this version needs must be there, not silently zero or null.
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES);

    private AnalysisJobData() {}

    /** Payload for a days-based classification run. */
    public static String days(int days) {
        return encode(new DaysRun(days));
    }

    /** Payload for a windowed Support Summary refresh. */
    public static String window(LocalDate from, LocalDate to) {
        return encode(new WindowRun(from, to));
    }

    private static String encode(Parsed payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not encode async job payload " + payload, e);
        }
    }

    /** @return the parsed payload, or null if it is in neither known format. */
    public static @Nullable Parsed parse(String data) {
        String trimmed = data.trim();
        if (trimmed.startsWith("{")) {
            try {
                return MAPPER.readValue(trimmed, Parsed.class);
            } catch (JsonProcessingException e) {
                return null;
            }
        }
        try {
            return new DaysRun(Integer.parseInt(trimmed));
        } catch (IllegalArgumentException e) {
            // NumberFormatException, or a parsable but invalid day count
            return null;
        }
    }

    /** A parsed {@code async_job.data} payload. */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = DaysRun.class, name = "days"),
        @JsonSubTypes.Type(value = WindowRun.class, name = "window")
    })
    public sealed interface Parsed permits DaysRun, WindowRun {}

    /** Classify closed tickets last interacted with in the last {@code days} days. */
    public record DaysRun(int days) implements Parsed {
        public DaysRun {
            if (days < 1) {
                throw new IllegalArgumentException("days must be at least 1, got: " + days);
            }
        }
    }

    /** Refresh the Support Summary for tickets raised between {@code from} and {@code to}. */
    public record WindowRun(LocalDate from, LocalDate to) implements Parsed {
        public WindowRun {
            requireNonNull(from, "from");
            requireNonNull(to, "to");
        }
    }
}
