package com.coreeng.supportbot.summary;

import com.google.common.collect.ImmutableList;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * What the page can say about the prose summary right now.
 *
 * <p>Breakdowns are always available, so this only ever describes the summary section: it renders
 * the text, a progress indicator, or an error — never blocks the rest of the page.
 */
public sealed interface SummaryState {

    /**
     * A cached summary whose fingerprint still matches the window's data.
     *
     * @param content the prose as plain text, citation markup removed
     * @param segments the same prose split into plain runs and cited claims; empty when it cites
     *     nothing, so the page renders {@code content} as it is
     */
    record Ready(String content, String model, @Nullable Instant generatedAt, ImmutableList<SummarySegment> segments)
            implements SummaryState {

        /** A summary with no citations. */
        public Ready(String content, String model, @Nullable Instant generatedAt) {
            this(content, model, generatedAt, ImmutableList.of());
        }
    }

    /**
     * A refresh is running (possibly one started by another visitor, possibly for another window —
     * the lock is global, so everyone waits on the same run and then re-polls).
     *
     * @param phase what the run is currently doing
     * @param analysedThreads threads classified so far in the backfill, null when not yet known
     * @param totalThreads threads the backfill found to classify, null when not yet known
     */
    record Generating(
            Phase phase,
            @Nullable Integer analysedThreads,
            @Nullable Integer totalThreads) implements SummaryState {}

    /** The last attempt for this window failed; it will be retried when the window's data changes. */
    record Unavailable(String error) implements SummaryState {}

    /** Stage of a refresh, for a meaningful progress message. */
    enum Phase {
        /** Backfilling classifications for tickets in the scope. */
        CLASSIFYING,
        /** Asking the LLM for the prose summary. */
        SUMMARISING
    }
}
