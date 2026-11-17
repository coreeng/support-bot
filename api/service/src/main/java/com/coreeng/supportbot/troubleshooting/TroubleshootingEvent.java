package com.coreeng.supportbot.troubleshooting;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/** One entry of a session's append-only log. {@code actor} is an engineer email, {@code router}, {@code slack:<id>} or {@code system}. */
public record TroubleshootingEvent(
        long sessionId, long seq, TroubleshootingEventType type, String actor, JsonNode payload, Instant createdAt) {

    public String text(String field) {
        JsonNode value = payload.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }
}
