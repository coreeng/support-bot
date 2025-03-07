package com.coreeng.supportbot.escalation;

import com.fasterxml.jackson.annotation.JsonValue;

public record EscalationId(
    @JsonValue long id
) {
    public String render() {
        return "ID-" + id;
    }
}
