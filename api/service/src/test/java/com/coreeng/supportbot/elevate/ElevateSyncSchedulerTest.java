package com.coreeng.supportbot.elevate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.config.ElevateProps;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

class ElevateSyncSchedulerTest {
    private static final Instant NOW = Instant.parse("2026-07-13T10:15:30Z");

    private final ElevateJobs jobs = mock(ElevateJobs.class);
    private final TaskScheduler taskScheduler = mock(TaskScheduler.class);
    private final ElevateSyncScheduler scheduler =
            new ElevateSyncScheduler(configuredProps(), jobs, taskScheduler, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void startsOnlyOnceWithAnImmediateFirstExecution() {
        ScheduledFuture<?> scheduled = mock(ScheduledFuture.class);
        doReturn(scheduled)
                .when(taskScheduler)
                .schedule(any(Runnable.class), any(org.springframework.scheduling.Trigger.class));

        scheduler.start();
        scheduler.start();

        assertThat(scheduler.nextExecution()).isEqualTo(NOW);
        verify(taskScheduler).schedule(any(Runnable.class), any(org.springframework.scheduling.Trigger.class));
    }

    @Test
    void failuresBackOffThenStayOnTheCappedRetryCadenceUntilSuccess() {
        when(jobs.syncInsights()).thenReturn(false, false, false, false, false, true);

        scheduler.runSync();
        assertThat(scheduler.nextExecution()).isEqualTo(NOW.plusSeconds(30));
        scheduler.runSync();
        assertThat(scheduler.nextExecution()).isEqualTo(NOW.plusSeconds(60));
        scheduler.runSync();
        assertThat(scheduler.nextExecution()).isEqualTo(NOW.plusSeconds(120));
        scheduler.runSync();
        assertThat(scheduler.nextExecution()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        scheduler.runSync();
        assertThat(scheduler.nextExecution()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));

        scheduler.runSync();

        assertThat(scheduler.nextExecution()).isEqualTo(NOW.plus(Duration.ofHours(12)));
        verify(jobs, times(6)).syncInsights();
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
