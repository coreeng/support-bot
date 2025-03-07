package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.escalation.Escalation;
import com.coreeng.supportbot.escalation.EscalationStatus;
import com.google.common.collect.ImmutableList;

public record DetailedTicket(
    Ticket ticket,
    ImmutableList<Escalation> escalations,
    boolean escalated
) {
    public DetailedTicket(Ticket ticket, boolean escalated) {
        this(ticket, ImmutableList.of(), escalated);
    }

    public DetailedTicket(Ticket ticket, ImmutableList<Escalation> escalations) {
        this(
            ticket,
            escalations,
            escalations.stream().anyMatch(e -> e.status() != EscalationStatus.resolved)
        );
    }
}
