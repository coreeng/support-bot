package com.coreeng.supportbot.erasure;

import java.time.Instant;
import java.util.UUID;

public record ErasureRequestResponse(UUID requestId, ErasureRequestStatus status, Instant requestedAt) {

    public static ErasureRequestResponse from(ErasureRequest request) {
        return new ErasureRequestResponse(request.id(), request.status(), request.requestedAt());
    }
}
