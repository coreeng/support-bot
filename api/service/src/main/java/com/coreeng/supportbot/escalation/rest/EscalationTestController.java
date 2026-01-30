package com.coreeng.supportbot.escalation.rest;

import com.coreeng.supportbot.escalation.Escalation;
import com.coreeng.supportbot.escalation.EscalationRepository;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketQueryService;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"functionaltests", "nft"})
@RequestMapping("/test/escalation")
@RequiredArgsConstructor
public class EscalationTestController {
    private final EscalationRepository escalationRepository;
    private final TicketQueryService ticketQueryService;

    @PostMapping
    @Transactional
    public ResponseEntity<Void> escalate(@RequestBody EscalationToCreate req) {
        var ticketId = new TicketId(req.ticketId());
        var ticket = ticketQueryService.findById(ticketId);
        if (ticket == null) {
            return ResponseEntity.notFound().build();
        }
        Escalation escalation = Escalation.createNew(
                ticketId,
                req.team(),
                ImmutableList.copyOf(req.tags()),
                ticket.queryRef()
            ).toBuilder()
            .createdMessageTs(MessageTs.of(req.createdMessageTs()))
            .build();
        escalationRepository.createIfNotExists(escalation);

        return ResponseEntity.ok().build();
    }

    public record EscalationToCreate(
        long ticketId,
        String team,
        String createdMessageTs,
        ImmutableList<String> tags
    ) {
    }
}


