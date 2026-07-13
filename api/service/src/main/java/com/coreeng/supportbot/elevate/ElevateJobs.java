package com.coreeng.supportbot.elevate;

import com.coreeng.supportbot.config.ElevateProps;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ElevateJobs {
    private final ElevateProps props;
    private final ElevateClient client;
    private final ElevateRepository repository;
    private final ElevateErrorSanitizer errorSanitizer;
    private final Clock clock;

    @Scheduled(
            fixedDelayString = "${elevate.status-interval:1h}",
            initialDelayString = "0",
            scheduler = "elevateStatusScheduler")
    public void reportStatus() {
        if (!props.configured()) {
            log.debug("Skipping Elevate status report because Elevate is not configured");
            return;
        }

        Instant attemptedAt = clock.instant();
        try {
            client.reportStatus();
            Instant completedAt = clock.instant();
            repository.recordPingSuccess(attemptedAt, completedAt);
            log.info("Elevate status report succeeded");
        } catch (RuntimeException failure) {
            String error = errorSanitizer.sanitize(failure);
            repository.recordPingFailure(attemptedAt, error);
            log.warn("Elevate status report failed: {}", error);
        }
    }

    @Scheduled(
            fixedDelayString = "${elevate.sync-interval:12h}",
            initialDelayString = "0",
            scheduler = "elevateSyncScheduler")
    public void syncInsights() {
        if (!props.configured()) {
            log.debug("Skipping Elevate insights sync because Elevate is not configured");
            return;
        }

        Instant attemptedAt = clock.instant();
        try {
            ElevateSnapshot snapshot = client.fetchSnapshot();
            Instant completedAt = clock.instant();
            repository.replaceSnapshot(snapshot, attemptedAt, completedAt);
            log.info(
                    "Elevate insights sync succeeded ({} products, {} users, {} journeys)",
                    snapshot.products().size(),
                    snapshot.users().size(),
                    snapshot.journeys().size());
        } catch (RuntimeException failure) {
            String error = errorSanitizer.sanitize(failure);
            repository.recordSyncFailure(attemptedAt, error);
            log.warn("Elevate insights sync failed: {}", error);
        }
    }
}
