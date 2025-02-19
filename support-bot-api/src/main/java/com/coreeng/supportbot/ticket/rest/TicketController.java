package com.coreeng.supportbot.ticket.rest;

import com.coreeng.supportbot.ticket.DetailedTicket;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketQueryService;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.coreeng.supportbot.ticket.TicketsQuery;
import com.coreeng.supportbot.util.Page;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/ticket")
@RequiredArgsConstructor
public class TicketController {
    private final TicketQueryService queryService;
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
        Page<TicketUI> result = queryService.findDetailedTicketByQuery(
            TicketsQuery.builder()
                .page(page)
                .pageSize(pageSize)
                .ids(ImmutableList.copyOf(ids))
                .dateFrom(dateFrom)
                .dateTo(dateTo)
                .status(status)
                .impacts(ImmutableList.copyOf(impacts))
                .teams(ImmutableList.copyOf(teams))
                .escalated(escalated)
                .build()
        ).map(mapper::mapToUI);
        return ResponseEntity.ok(result);
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
