package com.coreeng.supportbot.docanalysis;

import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * What the documentation analysis run is doing, as this instance knows it.
 *
 * @param active the run in progress, null when none is
 * @param stopped why the last run stopped early or the last start was refused; null when it
 *     finished, while a run is active, and after a restart
 */
public record DocAnalysisRunState(
        @Nullable Progress active, @Nullable StopReason stopped) {

    public static final DocAnalysisRunState IDLE = new DocAnalysisRunState(null, null);

    public boolean running() {
        return active != null;
    }

    /**
     * @param done tickets attempted so far, whatever came of them
     * @param total tickets that were due when the run started; zero until it has looked
     */
    public record Progress(LocalDate from, LocalDate to, int done, int total) {}

    public sealed interface StopReason {

        /** @param reason the source's own, safe to show */
        record SourceUnavailable(String reason) implements StopReason {}

        /** Five tickets in a row failed on the provider; no run starts before {@code retryAt}. */
        record ProviderOutage(Instant retryAt) implements StopReason {}

        record DailyLimitReached() implements StopReason {}

        /** Anything else; the cause is in the server log only. */
        record Failed() implements StopReason {}
    }
}
