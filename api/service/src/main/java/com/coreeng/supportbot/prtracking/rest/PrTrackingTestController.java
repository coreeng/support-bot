package com.coreeng.supportbot.prtracking.rest;

import com.coreeng.supportbot.prtracking.PrTrackingRecord;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"functionaltests", "nft"})
@ConditionalOnProperty(name = "pr-review-tracking.enabled", havingValue = "true")
@RequestMapping("/test/prtracking")
@RequiredArgsConstructor
public class PrTrackingTestController {
    private final PrTrackingTestService prTrackingTestService;

    @PostMapping("/poll")
    public void triggerPoll() {
        prTrackingTestService.triggerPoll();
    }

    @PostMapping("/record")
    public PrTrackingRecord createRecord(@RequestBody PrTrackingToCreate request) {
        return prTrackingTestService.createRecord(request);
    }

    @GetMapping("/record/{id}")
    public PrTrackingRecord getRecord(@PathVariable long id) {
        return prTrackingTestService.getRecord(id);
    }

    @PostMapping("/cleanup")
    public void cleanupRecords() {
        prTrackingTestService.cleanupRecords();
    }

    @PostMapping("/record/{id}/close")
    public PrTrackingRecord closeRecord(@PathVariable long id) {
        return prTrackingTestService.closeRecord(id);
    }

    public record PrTrackingToCreate(
            long ticketId,
            @Nullable String provider,
            String githubRepo,
            int prNumber,
            Instant prCreatedAt,
            Instant slaDeadline,
            String owningTeam,
            @Nullable Boolean canAutoCloseTicket,
            @Nullable String status,
            @Nullable Boolean codeownerReviewRequested) {}
}
