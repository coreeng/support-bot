package com.coreeng.supportbot.ticket.rest;

import com.coreeng.supportbot.slack.SlackId;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackGetMessageByTsRequest;
import com.coreeng.supportbot.ticket.*;
import com.coreeng.supportbot.util.Page;
import com.google.common.collect.ImmutableList;
import com.slack.api.model.Message;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/ticket")
@RequiredArgsConstructor
public class TicketController {
    private final TicketQueryService queryService;
    private final TicketUpdateService ticketUpdateService;
    private final TicketUIMapper mapper;
    private final TicketRepository ticketRepository;
    private final SlackClient slackClient;
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

    @GetMapping("/{id}/team-suggestions")
    public ResponseEntity<TicketTeamSuggestionsUI> getTeamSuggestions(@PathVariable TicketId id) {
        Ticket ticket = ticketRepository.findTicketById(id);
        if (ticket == null) {
            return ResponseEntity.notFound().build();
        }

        TicketTeamsSuggestion suggestion;
        try {
            Message queryMessage =
                    slackClient.getMessageByTs(new SlackGetMessageByTsRequest(ticket.channelId(), ticket.queryTs()));
            SlackId authorId = resolveAuthorId(queryMessage);

            if (authorId != null && !SlackId.SLACKBOT.equals(authorId)) {
                suggestion = teamSuggestionsService.getTeamSuggestions("", authorId);
            } else {
                suggestion = teamSuggestionsService.getFallbackSuggestions("");
            }
        } catch (Exception e) {
            log.atError()
                    .setCause(e)
                    .addKeyValue("ticketId", id.id())
                    .log("Error resolving team suggestions, returning fallback");
            suggestion = teamSuggestionsService.getFallbackSuggestions("");
        }

        return ResponseEntity.ok(new TicketTeamSuggestionsUI(suggestion.userTeams(), suggestion.otherTeams()));
    }

    @Nullable private SlackId resolveAuthorId(Message message) {
        if (message.getUser() != null) {
            return SlackId.user(message.getUser());
        }
        if (message.getBotId() != null) {
            return SlackId.bot(message.getBotId());
        }
        return null;
    }
}
