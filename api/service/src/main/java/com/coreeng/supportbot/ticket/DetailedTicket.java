package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.escalation.Escalation;
import com.coreeng.supportbot.escalation.EscalationStatus;
import com.google.common.collect.ImmutableList;

public record DetailedTicket(
    Ticket ticket,
    ImmutableList<Escalation> escalations
) {
    public boolean escalated() {
        return escalations.stream()
            .anyMatch(e -> e.status() != EscalationStatus.resolved);
    }
}
