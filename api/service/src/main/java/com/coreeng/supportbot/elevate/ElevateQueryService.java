package com.coreeng.supportbot.elevate;

import com.coreeng.supportbot.config.ElevateProps;
import com.coreeng.supportbot.util.Page;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Read boundary for Elevate connection status and persisted Insights snapshots. */
@Service
@RequiredArgsConstructor
public class ElevateQueryService {
    private final ElevateProps props;
    private final ElevateRepository repository;

    public boolean configured() {
        return props.configured();
    }

    public ElevateStatusResponse status() {
        ElevateStoredStatus storedStatus = repository.getStoredStatus();
        ElevateSyncState state = storedStatus.state();
        return new ElevateStatusResponse(
                props.configured(),
                props.configured() ? props.baseUrl() : null,
                props.statusInterval().toString(),
                props.syncInterval().toString(),
                state.lastPingAttemptAt(),
                state.lastPingSuccessAt(),
                state.lastPingSucceeded(),
                state.lastPingError(),
                state.lastSyncAttemptAt(),
                state.lastSyncSuccessAt(),
                state.lastSyncSucceeded(),
                state.lastSyncError(),
                storedStatus.snapshotVersion(),
                storedStatus.counts(),
                storedStatus.integrity());
    }

    public Page<ElevateProductSummary> products(UUID snapshotVersion, ElevateReadQuery query) {
        return repository.findProducts(snapshotVersion, query);
    }

    public Optional<ElevateProductSummary> product(UUID snapshotVersion, String productId) {
        return repository.findProduct(snapshotVersion, productId);
    }

    public Page<ElevateJourneySummary> productJourneys(UUID snapshotVersion, String productId, ElevateReadQuery query) {
        return repository.findProductJourneys(snapshotVersion, productId, query);
    }

    public Page<ElevateUserSummary> productUsers(UUID snapshotVersion, String productId, ElevateReadQuery query) {
        return repository.findProductUsers(snapshotVersion, productId, query);
    }

    public Optional<ElevateJourneySummary> journey(UUID snapshotVersion, String journeyId) {
        return repository.findJourney(snapshotVersion, journeyId);
    }

    public Page<ElevateUserSummary> journeyUsers(UUID snapshotVersion, String journeyId, ElevateReadQuery query) {
        return repository.findJourneyUsers(snapshotVersion, journeyId, query);
    }

    public Optional<ElevateUserSummary> user(UUID snapshotVersion, UUID userId) {
        return repository.findUser(snapshotVersion, userId);
    }

    public Page<ElevateJourneySummary> userJourneys(UUID snapshotVersion, UUID userId, ElevateReadQuery query) {
        return repository.findUserJourneys(snapshotVersion, userId, query);
    }

    public Page<ElevateIntegrityItem> integrity(
            UUID snapshotVersion, ElevateIntegrityType type, ElevateReadQuery query) {
        return repository.findIntegrity(snapshotVersion, type, query);
    }
}
