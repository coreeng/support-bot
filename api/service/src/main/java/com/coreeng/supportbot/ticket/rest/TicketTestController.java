package com.coreeng.supportbot.ticket.rest;

import static java.util.Objects.requireNonNull;

import com.coreeng.supportbot.ticket.TicketQueryService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.ticket.DetailedTicket;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketProcessingService;
import com.coreeng.supportbot.ticket.TicketRepository;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.coreeng.supportbot.ticket.TicketSubmission;
import com.coreeng.supportbot.ticket.TicketSubmitResult;
import com.google.common.collect.ImmutableList;

import lombok.RequiredArgsConstructor;

@RestController
@Profile("functionaltests")
@RequestMapping("/test/ticket")
@RequiredArgsConstructor
public class TicketTestController {
    private final TicketRepository repository;
    private final TicketQueryService queryService;
    private final TicketUIMapper mapper;
    private final TicketProcessingService ticketProcessingService;

    @PostMapping
    @Transactional
    public TicketUI createTicket(
        @RequestBody TicketToCreate ticketToCreate
    ) {
        Ticket ticket = repository.createTicketIfNotExists(Ticket.createNew(
                MessageTs.of(ticketToCreate.queryTs()),
                ticketToCreate.channelId()
            ).toBuilder()
            .createdMessageTs(MessageTs.ofOrNull(ticketToCreate.createdMessageTs()))
            .build());
        DetailedTicket detailedTicket = queryService.findDetailedById(ticket.id());
        String queryText = detailedTicket != null ? queryService.fetchQueryText(detailedTicket.ticket()) : null;
        return mapper.mapToUI(requireNonNull(detailedTicket), queryText);
    }

    @PatchMapping("/update")
    @Transactional
    public ResponseEntity<?> updateTicket(
        @RequestBody TicketToUpdate request
    ) {
        TicketId ticketId = new TicketId(request.ticketId());
        TicketSubmission submission = TicketSubmission.builder()
            .ticketId(ticketId)
            .status(request.status())
            .authorsTeam(request.authorsTeam())
            .tags(ImmutableList.copyOf(request.tags()))
            .impact(request.impact())
            .confirmed(true)
            .build();

        TicketSubmitResult result = ticketProcessingService.submit(submission);
        if (!(result instanceof TicketSubmitResult.Success)) {
            return ResponseEntity.internalServerError()
                .body("Expected a successful result, got: " + result);
        }
        DetailedTicket ticket = queryService.findDetailedById(ticketId);
        return ResponseEntity.ok(mapper.mapToUI(requireNonNull(ticket)));
    }

    public record TicketToCreate(
        String queryTs,
        String createdMessageTs,
        String channelId
    ) {
    }

    public record TicketToUpdate(
        long ticketId,
        TicketStatus status,
        String authorsTeam,
        ImmutableList<String> tags,
        String impact
    ) {
    }
}
