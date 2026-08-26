package com.coreeng.supportbot.analysis;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Encoding of the {@code async_job.data} payload for the shared {@code "analysis"} job.
 *
 * <p>Two shapes exist. A bare integer is a days-based classification run — the original format,
 * still written by {@code POST /analysis/run}. {@code window:<from>:<to>} is a Support Summary
 * refresh over an explicit date range.
 *
 * <p>Parsing is total: an unrecognised payload yields {@code null} rather than throwing, because the
 * one caller that reads it is the startup resume, and a row left behind by a different version of
 * the service must be cleaned up rather than crash the boot.
 */
public final class AnalysisJobData {

    private static final String WINDOW_PREFIX = "window:";

    private AnalysisJobData() {}

    /** Payload for a days-based classification run. */
    public static String days(int days) {
        return Integer.toString(days);
    }

    /** Payload for a windowed Support Summary refresh. */
    public static String window(LocalDate from, LocalDate to) {
        return WINDOW_PREFIX + from + ":" + to;
    }

    /** @return the parsed payload, or null if it is in neither known format. */
    public static @Nullable Parsed parse(String data) {
        String trimmed = data.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith(WINDOW_PREFIX)) {
            String[] parts = trimmed.substring(WINDOW_PREFIX.length()).split(":", -1);
            if (parts.length != 2) {
                return null;
            }
            try {
                return new WindowRun(LocalDate.parse(parts[0]), LocalDate.parse(parts[1]));
            } catch (DateTimeParseException e) {
                return null;
            }
        }
        try {
            return new DaysRun(Integer.parseInt(trimmed));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** A parsed {@code async_job.data} payload. */
    public sealed interface Parsed permits DaysRun, WindowRun {}

    /** Classify closed tickets last interacted with in the last {@code days} days. */
    public record DaysRun(int days) implements Parsed {}

    /** Refresh the Support Summary for tickets raised between {@code from} and {@code to}. */
    public record WindowRun(LocalDate from, LocalDate to) implements Parsed {}
}
