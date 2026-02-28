package com.coreeng.supportbot.prtracking.rest;

import static com.coreeng.supportbot.dbschema.Tables.PR_TRACKING;

import com.coreeng.supportbot.prtracking.NewPrTracking;
import com.coreeng.supportbot.prtracking.PrLifecyclePoller;
import com.coreeng.supportbot.prtracking.PrTrackingRecord;
import com.coreeng.supportbot.prtracking.PrTrackingRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"functionaltests", "nft"})
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
        return prTrackingRepository.insert(new NewPrTracking(
                request.ticketId(),
                request.githubRepo(),
                request.prNumber(),
                request.prCreatedAt(),
                request.slaDeadline(),
                request.owningTeam()));
    }

    @PostMapping("/cleanup")
    public void cleanupRecords() {
        dsl.deleteFrom(PR_TRACKING).execute();
    }

    public record PrTrackingToCreate(
            long ticketId,
            String githubRepo,
            int prNumber,
            Instant prCreatedAt,
            Instant slaDeadline,
            String owningTeam) {}
}
