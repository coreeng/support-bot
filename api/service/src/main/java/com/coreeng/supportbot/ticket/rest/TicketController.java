package com.coreeng.supportbot.ticket.rest;

import com.coreeng.supportbot.ticket.*;
import com.coreeng.supportbot.util.Page;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

import static com.google.common.collect.ImmutableList.toImmutableList;

@RestController
@RequestMapping("/ticket")
@RequiredArgsConstructor
public class TicketController {
    private final TicketQueryService queryService;
    private final TicketUpdateService ticketUpdateService;
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

        Page<DetailedTicket> detailedTicketsPage = queryService.findDetailedTicketByQuery(ticketQuery);

        ImmutableList<TicketUI> ticketUIs = detailedTicketsPage.content().stream()
                .map(mapper::mapToUI)
                .collect(toImmutableList());

        Page<TicketUI> ticketUIPage = new Page<>(
                ticketUIs,
                detailedTicketsPage.page(),
                detailedTicketsPage.totalPages(),
                detailedTicketsPage.totalElements()
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

    @PatchMapping("/{id}")
    @Transactional
    public ResponseEntity<?> updateTicket(@PathVariable TicketId id, @RequestBody TicketUpdateRequest request) {
        try {
            TicketUI ticket = ticketUpdateService.update(id, request);
            return ResponseEntity.ok(ticket);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
