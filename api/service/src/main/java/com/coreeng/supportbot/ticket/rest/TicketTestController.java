package com.coreeng.supportbot.ticket.rest;

import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.ticket.DetailedTicket;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketRepository;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("functionaltests")
@RequestMapping("/test/ticket")
@RequiredArgsConstructor
public class TicketTestController {
    private final TicketRepository repository;
    private final TicketUIMapper mapper;

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
        DetailedTicket detailedTicket = repository.findDetailedById(ticket.id());
        return mapper.mapToUI(detailedTicket);
    }

    public record TicketToCreate(
        String queryTs,
        String createdMessageTs,
        String channelId
    ) {
    }
}
