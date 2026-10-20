package com.coreeng.supportbot.erasure;

import java.time.Instant;
import java.util.UUID;

/** An Erasure Request accepted from Elevate's erasure orchestrator (ADR-009). */
public record ErasureRequest(
        UUID id,
        String currentUserEmail,
        String newUserEmail,
        ErasureRequestStatus status,
        Instant requestedAt,
        long version) {

    /** Leaves out the emails so a logged request cannot disclose them. */
    @Override
    public String toString() {
        return "ErasureRequest[id=" + id + ", status=" + status + ", requestedAt=" + requestedAt + ", version="
                + version + "]";
    }
}
