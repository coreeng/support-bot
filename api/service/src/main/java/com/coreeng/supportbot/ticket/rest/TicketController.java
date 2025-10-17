package com.coreeng.supportbot.ticket.rest;

import com.coreeng.supportbot.escalation.Escalation;
import com.coreeng.supportbot.escalation.EscalationQuery;
import com.coreeng.supportbot.escalation.EscalationQueryService;
import com.coreeng.supportbot.ticket.*;
import com.coreeng.supportbot.util.Page;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

import static com.google.common.collect.ImmutableList.toImmutableList;

@RestController
@RequestMapping("/ticket")
@RequiredArgsConstructor
public class TicketController {
    private final TicketQueryService queryService;
    private final EscalationQueryService escalationQueryService;
    private final TicketUIMapper mapper;

    @GetMapping
    public ResponseEntity<Page<TicketUI>> list(
            @RequestParam(defaultValue = "0") long page,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false, defaultValue = "") List<TicketId> ids,
            @RequestParam(required = false) LocalDate dateFrom,
            @RequestParam(required = false) LocalDate dateTo,
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) Boolean escalated,
            @RequestParam(required = false, defaultValue = "") List<String> impacts,
            @RequestParam(required = false, defaultValue = "") List<String> teams
    ) {
        TicketsQuery ticketQuery = TicketsQuery.builder()
                .page(page)
                .pageSize(pageSize)
                .ids(ImmutableList.copyOf(ids))
                .dateFrom(dateFrom)
                .dateTo(dateTo)
                .status(status)
                .impacts(ImmutableList.copyOf(impacts))
                .teams(ImmutableList.copyOf(teams))
                .escalated(escalated)
                .build();

        Page<Ticket> ticketsPage = queryService.findByQuery(ticketQuery);

        ImmutableList<TicketId> ticketIds = ticketsPage.content().stream()
                .map(Ticket::id)
                .collect(toImmutableList());

        Page<Escalation> escalationsPage = escalationQueryService.findByQuery(
                EscalationQuery.builder()
                        .ticketIds(ticketIds)
                        .unlimited(true)
                        .build()
        );

        Multimap<TicketId, Escalation> escalationsByTicket = Multimaps.index(
                escalationsPage.content(), Escalation::ticketId
        );

        ImmutableList<TicketUI> ticketUIs = ticketsPage.content().stream()
                .map(ticket -> new DetailedTicket(ticket,
                        ImmutableList.copyOf(escalationsByTicket.get(ticket.id()))))
                .map(mapper::mapToUI)
                .collect(toImmutableList());

        Page<TicketUI> ticketUIPage = new Page<>(
                ticketUIs,
                ticketsPage.page(),
                ticketsPage.totalPages(),
                ticketsPage.totalElements()
        );

        return ResponseEntity.ok(ticketUIPage);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketUI> findById(@PathVariable TicketId id) {
        DetailedTicket ticket = queryService.findDetailedById(id);
        if (ticket == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(mapper.mapToUI(ticket));
    }
}
