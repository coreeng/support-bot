package com.coreeng.supportbot.troubleshooting;

import java.time.Instant;

public record TroubleshootingSession(
        long id, long ticketId, TroubleshootingMode mode, long lastSeq, Instant createdAt) {}
