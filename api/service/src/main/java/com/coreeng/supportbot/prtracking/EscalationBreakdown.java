package com.coreeng.supportbot.prtracking;

public record EscalationBreakdown(long totalPrTickets, long botEscalatedTickets, long manuallyEscalatedTickets) {
    public EscalationBreakdown {
        if (totalPrTickets < 0 || botEscalatedTickets < 0 || manuallyEscalatedTickets < 0) {
            throw new IllegalArgumentException("counts must not be negative");
        }
    }
}
