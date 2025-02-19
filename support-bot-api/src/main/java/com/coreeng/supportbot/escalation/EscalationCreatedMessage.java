package com.coreeng.supportbot.escalation;

import com.coreeng.supportbot.enums.Tag;
import com.google.common.collect.ImmutableList;

import java.time.Instant;

public record EscalationCreatedMessage(
    EscalationId escalationId,
    String slackTeamGroupId,
    EscalationStatus status,
    Instant statusChangedDate,
    ImmutableList<Tag> tags,
    String ticketQueryPermalink
) {
    public static EscalationCreatedMessage of(
        Escalation escalation,
        String slackTeamGroupId,
        String ticketQueryPermalink,
        ImmutableList<Tag> tags
    ) {
        return new EscalationCreatedMessage(
            escalation.id(),
            slackTeamGroupId,
            escalation.status(),
            escalation.lastStatusChangedAt(),
            tags,
            ticketQueryPermalink
        );
    }
}
