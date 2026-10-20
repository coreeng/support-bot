package com.coreeng.supportbot.erasure;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ErasureRequestTest {

    @Test
    void toStringOmitsTheEmails() {
        UUID id = UUID.randomUUID();
        ErasureRequest request = new ErasureRequest(
                id,
                "leaver@example.com",
                "erased@example.com",
                ErasureRequestStatus.IN_PROGRESS,
                Instant.parse("2026-10-15T09:30:00Z"));

        assertThat(request.toString())
                .contains(id.toString(), "IN_PROGRESS")
                .doesNotContain("leaver@example.com", "erased@example.com");
    }
}
