package com.coreeng.supportbot.erasure.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.erasure.ErasureRequest;
import com.coreeng.supportbot.erasure.ErasureRequestStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ErasureRequestMapperTest {

    @Test
    void mapsTheRequestToItsResponseWithoutTheEmails() {
        UUID id = UUID.randomUUID();
        Instant requestedAt = Instant.parse("2026-10-15T09:30:00Z");
        ErasureRequest request = new ErasureRequest(
                id, "leaver@example.com", "erased@example.com", ErasureRequestStatus.IN_PROGRESS, requestedAt, 0);

        assertThat(new ErasureRequestMapper().mapToResponse(request))
                .isEqualTo(new ErasureRequestResponse(id, ErasureRequestStatus.IN_PROGRESS, requestedAt));
    }
}
