package com.coreeng.supportbot.escalation.rest;

import com.coreeng.supportbot.escalation.EscalationSource;
import com.coreeng.supportbot.escalation.EscalationTestService;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"functionaltests", "nft"})
@RequestMapping("/test/escalation")
@RequiredArgsConstructor
public class EscalationTestController {
    private final EscalationTestService escalationTestService;

    @PostMapping
    public ResponseEntity<Void> escalate(@RequestBody EscalationToCreate req) {
        if (!escalationTestService.escalate(
                req.ticketId(), req.team(), req.createdMessageTs(), req.tags(), req.source())) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().build();
    }

    public record EscalationToCreate(
            long ticketId,
            String team,
            String createdMessageTs,
            ImmutableList<String> tags,
            @Nullable EscalationSource source) {}
}
