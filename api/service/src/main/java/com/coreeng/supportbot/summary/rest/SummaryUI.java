package com.coreeng.supportbot.summary.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The Support Summary page payload.
 *
 * @param from first day of the window (inclusive)
 * @param to last day of the window (inclusive)
 * @param totalTickets tickets raised in the window in a monitored channel
 * @param classifiedTickets those with a classification from the current prompt
 * @param unclassifiedTickets the remainder — still open, or awaiting backfill; exposed so the
 *     breakdowns visibly reconcile against the total instead of silently under-counting
 * @param drivers, categories, features breakdowns over the classified tickets
 * @param teams breakdown over every ticket raised, so it sums to {@code totalTickets}
 * @param summary the prose section, which carries its own state and never blocks the rest
 */
public record SummaryUI(
        LocalDate from,
        LocalDate to,
        long totalTickets,
        long classifiedTickets,
        long unclassifiedTickets,
        List<SummaryCountUI> drivers,
        List<SummaryCountUI> categories,
        List<SummaryCountUI> features,
        List<SummaryCountUI> teams,
        SummarySectionUI summary) {

    /** One bar of a ranked breakdown. */
    public record SummaryCountUI(String label, long count) {}

    /**
     * @param state one of {@code ready}, {@code generating}, {@code unavailable}
     * @param content the prose, present when ready
     * @param model the model that produced it, present when ready
     * @param generatedAt when it was produced, present when ready
     * @param progress present while generating
     * @param error present when unavailable
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SummarySectionUI(
            String state,
            @Nullable String content,
            @Nullable String model,
            @Nullable Instant generatedAt,
            @Nullable SummaryProgressUI progress,
            @Nullable String error) {}

    /**
     * @param phase {@code classifying} while the backfill runs, {@code summarising} while the model
     *     is being asked for the prose
     * @param analysedThreads threads classified so far, null until the backfill has counted them
     * @param totalThreads threads the backfill found to classify, null until counted
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SummaryProgressUI(
            String phase,
            @Nullable Integer analysedThreads,
            @Nullable Integer totalThreads) {}
}
