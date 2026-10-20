package com.coreeng.supportbot.erasure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ErasureRequestServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-15T09:30:00Z");
    private static final UUID REQUEST_ID = UUID.fromString("5f0c6a2e-2a51-4b8e-9d0a-3b7c1e4f8a10");

    @Mock
    private ErasureRequestRepository repository;

    private ErasureRequestService service;

    @BeforeEach
    void setUp() {
        service = new ErasureRequestService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void submitStoresANewRequestInProgressAndReturnsTheStoredRow() {
        ErasureRequest stored = request(REQUEST_ID, "leaver@example.com", NOW);
        when(repository.insertIfAbsent(any())).thenReturn(true);
        when(repository.findById(REQUEST_ID)).thenReturn(Optional.of(stored));

        ErasureRequest result =
                service.submit(new SubmitErasureRequest(REQUEST_ID, "leaver@example.com", "erased@example.com"));

        ArgumentCaptor<ErasureRequest> inserted = ArgumentCaptor.forClass(ErasureRequest.class);
        verify(repository).insertIfAbsent(inserted.capture());
        assertThat(inserted.getValue())
                .isEqualTo(new ErasureRequest(
                        REQUEST_ID,
                        "leaver@example.com",
                        "erased@example.com",
                        ErasureRequestStatus.IN_PROGRESS,
                        NOW,
                        0));
        assertThat(result).isEqualTo(stored);
    }

    @Test
    void resubmittingAnIdReturnsTheFirstRequestUnchanged() {
        Instant firstAcceptedAt = NOW.minusSeconds(3600);
        ErasureRequest first = request(REQUEST_ID, "first@example.com", firstAcceptedAt);
        when(repository.insertIfAbsent(any())).thenReturn(false);
        when(repository.findById(REQUEST_ID)).thenReturn(Optional.of(first));

        ErasureRequest result =
                service.submit(new SubmitErasureRequest(REQUEST_ID, "different@example.com", "erased@example.com"));

        assertThat(result).isEqualTo(first);
    }

    @Test
    void getReturnsAnAcceptedRequest() {
        ErasureRequest stored = request(REQUEST_ID, "leaver@example.com", NOW);
        when(repository.findById(REQUEST_ID)).thenReturn(Optional.of(stored));

        assertThat(service.get(REQUEST_ID)).isEqualTo(stored);
    }

    @Test
    void getRejectsAnUnknownId() {
        when(repository.findById(REQUEST_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(REQUEST_ID)).isInstanceOf(ErasureRequestNotFoundException.class);
    }

    private static ErasureRequest request(UUID id, String currentUserEmail, Instant requestedAt) {
        return new ErasureRequest(
                id, currentUserEmail, "erased@example.com", ErasureRequestStatus.IN_PROGRESS, requestedAt, 0);
    }
}
