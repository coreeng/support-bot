package com.coreeng.supportbot.prtracking.rest;

import static com.coreeng.supportbot.dbschema.Tables.PR_TRACKING;

import com.coreeng.supportbot.prtracking.NewPrTracking;
import com.coreeng.supportbot.prtracking.PrLifecyclePoller;
import com.coreeng.supportbot.prtracking.PrTrackingRecord;
import com.coreeng.supportbot.prtracking.PrTrackingRepository;
import com.coreeng.supportbot.dbschema.enums.PrTrackingStatus;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile({"functionaltests", "nft"})
@ConditionalOnProperty(name = "pr-review-tracking.enabled", havingValue = "true")
@RequestMapping("/test/prtracking")
@RequiredArgsConstructor
public class PrTrackingTestController {
    private final PrLifecyclePoller prLifecyclePoller;
    private final PrTrackingRepository prTrackingRepository;
    private final DSLContext dsl;

    @PostMapping("/poll")
    public void triggerPoll() {
        prLifecyclePoller.poll();
    }

    @PostMapping("/record")
    public PrTrackingRecord createRecord(@RequestBody PrTrackingToCreate request) {
        boolean canAutoClose = request.canAutoCloseTicket() == null || request.canAutoCloseTicket();
        PrTrackingRecord created = prTrackingRepository.insertIfAbsent(new NewPrTracking(
                request.ticketId(),
                request.githubRepo(),
                request.prNumber(),
                request.prCreatedAt(),
                request.slaDeadline(),
                request.owningTeam(),
                canAutoClose));
        if (created == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "PR tracking record already exists");
        }
        return created;
    }

    @GetMapping("/record/{id}")
    public PrTrackingRecord getRecord(@PathVariable long id) {
        PrTrackingRecord record = prTrackingRepository.findById(id);
        if (record == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PR tracking record not found");
        }
        return record;
    }

    @PostMapping("/cleanup")
    public void cleanupRecords() {
        dsl.deleteFrom(PR_TRACKING).execute();
    }

    @PostMapping("/record/{id}/close")
    public PrTrackingRecord closeRecord(@PathVariable long id) {
        // Uses the same repo method as the lifecycle poller so the test goes through the real
        // write path — nulling SLA fields, leaving has_sla untouched. See V15__pr_tracking_has_sla.sql.
        PrTrackingRecord existing = prTrackingRepository.findById(id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PR tracking record not found");
        }
        return prTrackingRepository.updateStatus(id, PrTrackingStatus.CLOSED, Instant.now(), existing.escalationId());
    }

    public record PrTrackingToCreate(
            long ticketId,
            String githubRepo,
            int prNumber,
            Instant prCreatedAt,
            Instant slaDeadline,
            String owningTeam,
            @Nullable Boolean canAutoCloseTicket) {}
}
