package com.coreeng.supportbot.elevate;

import com.coreeng.supportbot.config.ElevateProps;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ElevateStatusController {
    private final ElevateProps props;
    private final ElevateRepository repository;

    @GetMapping("/elevate/status")
    @PreAuthorize("hasAnyRole('LEADERSHIP', 'SUPPORT_ENGINEER')")
    public ElevateStatusResponse status() {
        ElevateStoredStatus storedStatus = repository.getStoredStatus();
        ElevateSyncState state = storedStatus.state();
        ElevateSnapshot snapshot = storedStatus.snapshot();
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
                snapshot.products(),
                snapshot.journeys(),
                snapshot.users());
    }
}
