package com.coreeng.supportbot.troubleshooting.rest;

import com.google.common.collect.ImmutableList;
import java.time.Instant;

public record TroubleshootingSessionUI(
        long id,
        long ticketId,
        String mode,
        long lastSeq,
        Instant createdAt,
        ImmutableList<TroubleshootingEventUI> events) {}
