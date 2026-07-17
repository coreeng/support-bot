package com.coreeng.supportbot.elevate;

import com.coreeng.supportbot.config.ElevateProps;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public final class ElevateSyncScheduler {
    private final ElevateProps props;
    private final ElevateJobs jobs;
    private final TaskScheduler scheduler;
    private final Clock clock;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicReference<Instant> nextExecution = new AtomicReference<>(Instant.EPOCH);
    private int consecutiveFailures;

    public ElevateSyncScheduler(
            ElevateProps props,
            ElevateJobs jobs,
            @Qualifier("elevateSyncScheduler") TaskScheduler scheduler,
            Clock clock) {
        this.props = props;
        this.jobs = jobs;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        nextExecution.set(clock.instant());
        ScheduledFuture<?> scheduled = scheduler.schedule(this::runSync, triggerContext -> nextExecution.get());
        if (scheduled == null) {
            started.set(false);
            throw new IllegalStateException("Elevate insights sync could not be scheduled");
        }
    }

    void runSync() {
        boolean succeeded;
        try {
            succeeded = jobs.syncInsights();
        } catch (RuntimeException failure) {
            log.warn(
                    "Elevate insights sync failed before it could record its result; continuing the retry cadence ({})",
                    failure.getClass().getSimpleName());
            succeeded = false;
        }
        Duration delay;
        if (succeeded) {
            consecutiveFailures = 0;
            delay = props.syncInterval();
        } else {
            consecutiveFailures++;
            delay = retryDelay(consecutiveFailures);
        }
        nextExecution.set(clock.instant().plus(delay));
        log.debug("Next Elevate insights sync scheduled in {}", delay);
    }

    Instant nextExecution() {
        return nextExecution.get();
    }

    private Duration retryDelay(int failureCount) {
        if (failureCount > props.syncRetryBurstAttempts()) {
            return props.syncRetryMaxDelay();
        }
        Duration delay = props.syncRetryInitialDelay();
        for (int retry = 1; retry < failureCount; retry++) {
            if (delay.compareTo(props.syncRetryMaxDelay().dividedBy(2)) > 0) {
                return props.syncRetryMaxDelay();
            }
            delay = delay.multipliedBy(2);
        }
        return delay.compareTo(props.syncRetryMaxDelay()) > 0 ? props.syncRetryMaxDelay() : delay;
    }
}
