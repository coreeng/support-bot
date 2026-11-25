package com.coreeng.supportbot.ticket.rest;

import com.coreeng.supportbot.rating.RatingService;
import com.coreeng.supportbot.rating.RatingTicketNotFoundException;
import com.coreeng.supportbot.ticket.*;
import com.coreeng.supportbot.util.Page;
import com.google.common.collect.ImmutableList;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ticket")
@RequiredArgsConstructor
public class TicketController {
    private final TicketQueryService queryService;
    private final TicketUpdateService ticketUpdateService;
    private final TicketProcessingService ticketProcessingService;
    private final TicketEscalationValidator ticketEscalationValidator;
    private final RatingService ratingService;
    private final TicketUIMapper mapper;
    private final TicketTeamSuggestionsService teamSuggestionsService;

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
        ImmutableList<TicketUI> ticketUIs = mapper.mapToUIList(detailedTicketsPage.content());

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
        return ResponseEntity.ok(mapper.mapToUI(ticket, queryText));
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

    @PostMapping("/{id}/rating")
    public ResponseEntity<?> submitRating(@PathVariable TicketId id, @RequestBody TicketRatingRequest request) {
        try {
            if (request.rating() == null) {
                throw new IllegalArgumentException("rating is required");
            }
            ratingService.save(id, request.rating());
            return ResponseEntity.ok().build();
        } catch (RatingTicketNotFoundException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{id}/escalation")
    public ResponseEntity<?> escalateTicket(
            @PathVariable TicketId id, @RequestBody TicketEscalationCreateRequest request) {
        if (queryService.findById(id) == null) {
            return ResponseEntity.notFound().build();
        }
        TicketEscalationValidator.ValidationResult validationResult =
                ticketEscalationValidator.validate(request.team(), request.tags());
        if (!validationResult.isValid()) {
            return ResponseEntity.badRequest().body(validationResult.errorMessage());
        }
        String team = Objects.requireNonNull(request.team());

        try {
            List<String> tags = Objects.requireNonNullElse(request.tags(), List.of());
            ticketProcessingService.escalate(EscalateRequest.builder()
                    .ticketId(id)
                    .team(team)
                    .tags(ImmutableList.copyOf(tags))
                    .build());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{id}/team-suggestions")
    public ResponseEntity<TicketTeamSuggestionsUI> getTeamSuggestions(@PathVariable TicketId id) {
        return teamSuggestionsService
                .getTeamSuggestionsForTicket(id)
                .map(s -> ResponseEntity.ok(TicketTeamSuggestionsUI.from(s)))
                .orElse(ResponseEntity.notFound().build());
    }
}
