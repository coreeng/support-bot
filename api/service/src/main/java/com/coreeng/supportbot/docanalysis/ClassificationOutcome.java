package com.coreeng.supportbot.docanalysis;

import com.coreeng.supportbot.ticket.TicketId;

/** What classifying one ticket came to. */
public sealed interface ClassificationOutcome {

    /** The result was saved. */
    record Classified(DocAnalysis.Result result) implements ClassificationOutcome {}

    /**
     * The failed attempt was saved.
     *
     * @param retryable false when trying again cannot help until the prompts or settings change
     */
    record AttemptFailed(DocAnalysis.FailedAttempt attempt, boolean retryable) implements ClassificationOutcome {}

    /**
     * A model call failed, in the classification call or in the source. Nothing was saved: whether
     * to try again, and what to record if not, is the caller's decision.
     */
    record ProviderFailed(TicketId ticketId, String promptId) implements ClassificationOutcome {}
}
