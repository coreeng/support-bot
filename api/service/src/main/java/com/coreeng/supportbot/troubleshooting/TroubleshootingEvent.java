package com.coreeng.supportbot.troubleshooting;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/**
 * One entry of a session's append-only log. {@code actor} is {@code slack:<id>}, {@code system}, or
 * {@code erased} once an erasure request has removed who it was.
 */
public record TroubleshootingEvent(
        long sessionId, long seq, TroubleshootingEventType type, String actor, JsonNode payload, Instant createdAt) {

    public String text(String field) {
        JsonNode value = payload.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }
}
