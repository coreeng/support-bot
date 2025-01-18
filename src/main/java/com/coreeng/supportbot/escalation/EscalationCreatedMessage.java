package com.coreeng.supportbot.escalation;

import com.coreeng.supportbot.enums.Tag;
import com.google.common.collect.ImmutableList;

import java.time.Instant;

public record EscalationCreatedMessage(
    EscalationId escalationId,
    String teamId,
    EscalationStatus status,
    Instant statusChangedDate,
    ImmutableList<Tag> tags,
    String ticketQueryPermalink
) {
    public static EscalationCreatedMessage of(
        Escalation escalation,
        String ticketQueryPermalink
    ) {
        return new EscalationCreatedMessage(
            escalation.id(),
            escalation.teamId(),
            escalation.status(),
            escalation.lastStatusChangedAt(),
            escalation.tags(),
            ticketQueryPermalink
        );
    }
}
