package com.coreeng.supportbot.erasure.rest;

import com.coreeng.supportbot.erasure.ErasureRequestStatus;
import java.time.Instant;
import java.util.UUID;

public record ErasureRequestResponse(UUID requestId, ErasureRequestStatus status, Instant requestedAt) {}
