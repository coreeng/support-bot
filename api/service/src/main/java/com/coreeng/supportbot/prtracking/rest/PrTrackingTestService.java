package com.coreeng.supportbot.prtracking.rest;

import static com.coreeng.supportbot.dbschema.Tables.PR_TRACKING;

import com.coreeng.supportbot.dbschema.enums.PrTrackingStatus;
import com.coreeng.supportbot.prtracking.NewPrTracking;
import com.coreeng.supportbot.prtracking.PrLifecyclePoller;
import com.coreeng.supportbot.prtracking.PrTrackingRecord;
import com.coreeng.supportbot.prtracking.PrTrackingRepository;
import com.coreeng.supportbot.prtracking.source.Provider;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Functional-test fixture boundary for PR lifecycle state. */
@Service
@Profile({"functionaltests", "nft"})
@ConditionalOnProperty(name = "pr-review-tracking.enabled", havingValue = "true")
@RequiredArgsConstructor
public class PrTrackingTestService {
    private final PrLifecyclePoller prLifecyclePoller;
    private final PrTrackingRepository prTrackingRepository;
    private final DSLContext dsl;

    public void triggerPoll() {
        prLifecyclePoller.poll();
    }

    public PrTrackingRecord createRecord(PrTrackingTestController.PrTrackingToCreate request) {
        boolean canAutoClose = request.canAutoCloseTicket() == null || request.canAutoCloseTicket();
        Provider provider = request.provider() == null ? Provider.GITHUB : Provider.fromStorage(request.provider());
        PrTrackingRecord created = prTrackingRepository.insertIfAbsent(new NewPrTracking(
                request.ticketId(),
                provider,
                request.githubRepo(),
                request.prNumber(),
                request.prCreatedAt(),
                request.slaDeadline(),
                request.owningTeam(),
                canAutoClose));
        if (created == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "PR tracking record already exists");
        }
        // Optionally seed the record in a non-OPEN status (e.g. AWAITING_MERGE with an already-breached
        // slaDeadline) so merge-SLA lifecycle tests are deterministic. Omitted/OPEN status leaves the
        // default insert untouched. AWAITING_MERGE/MERGE_ESCALATED go through enterMergePhase instead of
        // startSla so merge_phase_entered is correctly true for a record seeded already in the merge phase
        // — otherwise a test that then drives it through a changes-requested detour would hit the same
        // review/merge sla_remaining ambiguity this flag exists to resolve.
        String status = request.status();
        if (status != null && !"OPEN".equals(status)) {
            PrTrackingStatus target = PrTrackingStatus.valueOf(status);
            created = target == PrTrackingStatus.AWAITING_MERGE || target == PrTrackingStatus.MERGE_ESCALATED
                    ? prTrackingRepository.enterMergePhase(created.id(), target, request.slaDeadline())
                    : prTrackingRepository.startSla(created.id(), target, request.slaDeadline());
        }
        if (Boolean.TRUE.equals(request.codeownerReviewRequested())) {
            created = prTrackingRepository.markCodeownerReviewRequested(created.id());
        }
        return created;
    }

    public PrTrackingRecord getRecord(long id) {
        PrTrackingRecord record = prTrackingRepository.findById(id);
        if (record == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PR tracking record not found");
        }
        return record;
    }

    public void cleanupRecords() {
        dsl.deleteFrom(PR_TRACKING).execute();
    }

    public PrTrackingRecord closeRecord(long id) {
        PrTrackingRecord existing = getRecord(id);
        return prTrackingRepository.updateStatus(id, PrTrackingStatus.CLOSED, Instant.now(), existing.escalationId());
    }
}
