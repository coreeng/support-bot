package com.coreeng.supportbot.escalation;

import java.util.UUID;

public record EscalationId(
    UUID id
) {
    public static EscalationId createNew() {
        return new EscalationId(UUID.randomUUID());
    }
}
