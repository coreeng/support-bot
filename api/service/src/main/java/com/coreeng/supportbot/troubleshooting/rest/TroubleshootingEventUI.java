package com.coreeng.supportbot.troubleshooting.rest;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record TroubleshootingEventUI(long seq, String type, String actor, JsonNode payload, Instant createdAt) {}
