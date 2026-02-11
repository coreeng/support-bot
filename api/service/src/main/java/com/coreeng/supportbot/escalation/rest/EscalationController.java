package com.coreeng.supportbot.escalation.rest;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.coreeng.supportbot.escalation.*;
import com.coreeng.supportbot.ticket.*;
import com.coreeng.supportbot.util.Page;
import com.google.common.collect.ImmutableList;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/escalation")
@RequiredArgsConstructor
public class EscalationController {
    private final EscalationQueryService escalationQueryService;
    private final TicketQueryService ticketQueryService;
    private final EscalationUIMapper mapper;

    @GetMapping
    public Page<EscalationUI> list(
            @RequestParam(defaultValue = "0") Long page,
            @RequestParam(defaultValue = "10") Long pageSize,
            @RequestParam(defaultValue = "") List<EscalationId> ids,
            @Nullable @RequestParam(required = false) TicketId ticketId,
            @Nullable @RequestParam(required = false) LocalDate dateFrom,
            @Nullable @RequestParam(required = false) LocalDate dateTo,
            @Nullable @RequestParam(required = false) EscalationStatus status,
            @Nullable @RequestParam(required = false) String team) {
        Page<Escalation> escalationsPage = escalationQueryService.findByQuery(EscalationQuery.builder()
                .page(page)
                .pageSize(pageSize)
                .ids(ImmutableList.copyOf(ids))
                .ticketIds(ticketId != null ? ImmutableList.of(ticketId) : ImmutableList.of())
                .dateFrom(dateFrom)
                .dateTo(dateTo)
                .status(status)
                .team(team)
                .build());

        ImmutableList<TicketId> ticketIds =
                escalationsPage.content().stream().map(Escalation::ticketId).collect(toImmutableList());

        Map<TicketId, Ticket> ticketsById =
                ticketQueryService
                        .findByQuery(TicketsQuery.builder()
                                .unlimited(true)
                                .ids(ticketIds)
                                .build())
                        .content()
                        .stream()
                        .collect(Collectors.toMap(Ticket::id, t -> t));

        ImmutableList<EscalationUI> uiList = escalationsPage.content().stream()
                .map(esc -> {
                    Ticket ticket = ticketsById.get(esc.ticketId());

                    EscalationUI escalationUI = mapper.mapToUI(esc);

                    return escalationUI.toBuilder()
                            .escalatingTeam(
                                    ticket != null && ticket.team() != null
                                            ? ticket.team().toCode()
                                            : null)
                            .impact(ticket != null ? ticket.impact() : null)
                            .build();
                })
                .collect(toImmutableList());

        return new Page<>(uiList, page, uiList.size() / pageSize + 1, uiList.size());
    }
}
