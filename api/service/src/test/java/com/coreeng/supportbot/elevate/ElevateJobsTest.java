package com.coreeng.supportbot.elevate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.config.ElevateProps;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.scheduling.annotation.Scheduled;

class ElevateJobsTest {
    private static final Instant NOW = Instant.parse("2026-07-13T10:15:30Z");

    private final ElevateClient client = mock(ElevateClient.class);
    private final ElevateRepository repository = mock(ElevateRepository.class);
    private final ElevateProps props = configuredProps();
    private final ElevateErrorSanitizer sanitizer = new ElevateErrorSanitizer(props);
    private final ElevateJobs jobs =
            new ElevateJobs(props, client, repository, sanitizer, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void successfulSyncAtomicallyHandsCompleteSnapshotToRepository() {
        ElevateSnapshot snapshot = new ElevateSnapshot(List.of(), List.of(), List.of());
        when(client.fetchSnapshot()).thenReturn(snapshot);

        assertThat(jobs.syncInsights()).isTrue();

        InOrder sync = inOrder(repository, client);
        sync.verify(repository).recordSyncAttempt(NOW);
        sync.verify(client).fetchSnapshot();
        sync.verify(repository).replaceSnapshot(snapshot, NOW, NOW);
        verify(repository, never())
                .recordSyncFailure(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void failedCollectionRetainsLastGoodSnapshotAndRecordsSanitizedAttempt() {
        doThrow(new ElevateApiException("HTTP 401 Bearer token-value client_secret=secret-value esc_client"))
                .when(client)
                .fetchSnapshot();

        assertThat(jobs.syncInsights()).isFalse();

        verify(repository).recordSyncAttempt(NOW);
        verify(repository, never())
                .replaceSnapshot(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
        ArgumentCaptor<String> error = ArgumentCaptor.forClass(String.class);
        verify(repository).recordSyncFailure(org.mockito.ArgumentMatchers.eq(NOW), error.capture());
        assertThat(error.getValue())
                .doesNotContain("token-value", "secret-value", "esc_client")
                .contains("<redacted>");
    }

    @Test
    void oversizedPageUsesTheNormalSanitizedFailurePath() {
        String failure = "Elevate insights page exceeded the configured response size limit of 16777216 bytes";
        doThrow(new ElevateApiException(failure)).when(client).fetchSnapshot();

        assertThat(jobs.syncInsights()).isFalse();

        verify(repository, never())
                .replaceSnapshot(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
        verify(repository).recordSyncFailure(NOW, failure);
    }

    @Test
    void doesNotFetchWhenTheAttemptCannotBePersisted() {
        doThrow(new IllegalStateException("database unavailable"))
                .when(repository)
                .recordSyncAttempt(NOW);

        assertThatThrownBy(jobs::syncInsights)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        verify(client, never()).fetchSnapshot();
    }

    @Test
    void pingStoresAttemptAndSeparateSuccessTime() {
        jobs.reportStatus();

        verify(client).reportStatus();
        verify(repository).recordPingSuccess(NOW, NOW);
        verify(repository, never()).recordSyncAttempt(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unconfiguredJobsSkipWithoutChangingState() {
        ElevateProps disabledProps = new ElevateProps(
                "",
                "",
                "",
                Duration.ofSeconds(5),
                Duration.ofSeconds(30),
                16_777_216,
                67_108_864,
                Duration.ofMinutes(1),
                Duration.ofHours(1),
                Duration.ofHours(12),
                Duration.ofMinutes(10),
                100,
                20_000,
                100_000,
                3,
                Duration.ofSeconds(30),
                Duration.ofMinutes(5),
                "Support Bot",
                "http://localhost:3000",
                "dev");
        ElevateClient disabledClient = mock(ElevateClient.class);
        ElevateRepository disabledRepository = mock(ElevateRepository.class);
        ElevateJobs disabledJobs = new ElevateJobs(
                disabledProps,
                disabledClient,
                disabledRepository,
                new ElevateErrorSanitizer(disabledProps),
                Clock.fixed(NOW, ZoneOffset.UTC));

        disabledJobs.reportStatus();
        assertThat(disabledJobs.syncInsights()).isTrue();

        verifyNoInteractions(disabledClient, disabledRepository);
    }

    @Test
    void schedulesStatusImmediatelyAtTheConfiguredFixedDelay() throws ReflectiveOperationException {
        Method ping = ElevateJobs.class.getMethod("reportStatus");

        assertThat(ping.getAnnotation(Scheduled.class)).satisfies(schedule -> {
            assertThat(schedule.fixedDelayString()).isEqualTo("${elevate.status-interval:1h}");
            assertThat(schedule.initialDelayString()).isEqualTo("0");
            assertThat(schedule.scheduler()).isEqualTo("elevateStatusScheduler");
        });
    }

    private static ElevateProps configuredProps() {
        return new ElevateProps(
                "https://elevate.example.test",
                "esc_client",
                "secret-value",
                Duration.ofSeconds(5),
                Duration.ofSeconds(30),
                16_777_216,
                67_108_864,
                Duration.ofMinutes(1),
                Duration.ofHours(1),
                Duration.ofHours(12),
                Duration.ofMinutes(10),
                100,
                20_000,
                100_000,
                3,
                Duration.ofSeconds(30),
                Duration.ofMinutes(5),
                "Support Bot",
                "https://support.example.test",
                "1.2.3");
    }
}
