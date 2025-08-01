package com.coreeng.supportbot.escalation;

import com.coreeng.supportbot.enums.Tag;
import com.google.common.collect.ImmutableList;

import java.time.Instant;

public record EscalationCreatedMessage(
    EscalationId escalationId,
    String slackTeamGroupId
) {
    public static EscalationCreatedMessage of(
        Escalation escalation,
        String slackTeamGroupId
    ) {
        return new EscalationCreatedMessage(
            escalation.id(),
            slackTeamGroupId
        );
    }
}
