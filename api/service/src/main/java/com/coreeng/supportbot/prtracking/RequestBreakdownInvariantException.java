package com.coreeng.supportbot.prtracking;

/**
 * Base type for {@link RequestBreakdown} invariant violations. A violation means the funnel
 * aggregate returned counts that cannot describe a real support-request funnel (e.g. more PR
 * tickets than total tickets), which indicates a query or data regression rather than bad client
 * input — so the controller advice surfaces it as a server error, logged loudly, while the
 * dashboard degrades to a dash on the affected cards.
 */
public abstract sealed class RequestBreakdownInvariantException extends RuntimeException
        permits NegativeCountException, PrTicketsExceedSupportTicketsException, InterventionExceedsPrTicketsException {

    protected RequestBreakdownInvariantException(String message) {
        super(message);
    }
}
