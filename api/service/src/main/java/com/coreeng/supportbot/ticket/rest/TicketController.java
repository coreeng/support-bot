package com.coreeng.supportbot.ticket.rest;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.coreeng.supportbot.analysis.AnalysisRepository;
import com.coreeng.supportbot.ticket.*;
import com.coreeng.supportbot.util.Page;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ticket")
@RequiredArgsConstructor
public class TicketController {
    private final AnalysisRepository analysisRepository;
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
            @RequestParam(required = false, defaultValue = "") List<String> teams,
            @RequestParam(required = false) String assignedTo) {
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
                .assignedTo(assignedTo)
                .build();

        Page<DetailedTicket> detailedTicketsPage = queryService.findDetailedTicketByQuery(ticketQuery);
        ImmutableList<TicketId> ticketIds = detailedTicketsPage.content().stream()
                .map(detailedTicket -> detailedTicket.ticket().id())
                .collect(toImmutableList());
        ImmutableMap<TicketId, String> summariesByTicketId = analysisRepository.findSummariesByTicketIds(ticketIds);

        ImmutableList<TicketUI> ticketUIs = detailedTicketsPage.content().stream()
                .map(ticket -> mapper.mapToUI(
                        ticket, null, summariesByTicketId.get(ticket.ticket().id())))
                .collect(toImmutableList());

        Page<TicketUI> ticketUIPage = new Page<>(
                ticketUIs,
                detailedTicketsPage.page(),
                detailedTicketsPage.totalPages(),
                detailedTicketsPage.totalElements());

        return ResponseEntity.ok(ticketUIPage);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketUI> findById(@PathVariable TicketId id) {
        DetailedTicket ticket = queryService.findDetailedById(id);
        if (ticket == null) {
            return ResponseEntity.notFound().build();
        }
        String queryText = queryService.fetchQueryText(ticket.ticket());
        String summary = analysisRepository.findSummaryByTicketId(id);
        return ResponseEntity.ok(mapper.mapToUI(ticket, queryText, summary));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> updateTicket(
            @PathVariable TicketId id, @Nullable @RequestBody TicketUpdateRequest request) {
        try {
            TicketUI ticket = ticketUpdateService.update(id, request);
            return ResponseEntity.ok(ticket);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
