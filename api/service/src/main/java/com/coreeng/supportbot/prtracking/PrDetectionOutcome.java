package com.coreeng.supportbot.prtracking;

import com.google.common.collect.ImmutableList;

/**
 * Outcome of PR detection processing for a single Slack message.
 *
 * <p>Callers check {@link #shouldCloseTicket()} and, if true, use {@link #closingTags()} and
 * {@link #closingImpact()} to close the associated ticket. Keeping this logic in the caller avoids
 * a circular dependency between {@code PrDetectionService} and {@code TicketProcessingService}.
 */
public record PrDetectionOutcome(boolean shouldCloseTicket, ImmutableList<String> closingTags, String closingImpact) {

    /** A PR was successfully detected and SLA tracking started — no ticket closure needed. */
    public static PrDetectionOutcome tracked() {
        return new PrDetectionOutcome(false, ImmutableList.of(), "");
    }

    /** Nothing to act on — no PR links found, already tracked, or a fetch error occurred. */
    public static PrDetectionOutcome skipped() {
        return new PrDetectionOutcome(false, ImmutableList.of(), "");
    }
}
