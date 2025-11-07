package com.coreeng.supportbot.escalation;


import com.coreeng.supportbot.enums.EscalationTeam;

public record EscalationCreatedMessage(
    EscalationId escalationId,
    EscalationTeam team
) {
}
