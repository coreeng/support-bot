package com.coreeng.supportbot.escalation;

import lombok.Getter;

@Getter
public enum EscalationStatus {
    opened("Opened"),
    resolved("Resolved");

    private final String label;

    EscalationStatus(String label) {
        this.label = label;
    }
}
