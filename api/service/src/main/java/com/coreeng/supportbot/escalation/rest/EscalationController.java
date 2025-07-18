package com.coreeng.supportbot.escalation.rest;

import com.coreeng.supportbot.escalation.EscalationId;
import com.coreeng.supportbot.escalation.EscalationQuery;
import com.coreeng.supportbot.escalation.EscalationQueryService;
import com.coreeng.supportbot.escalation.EscalationStatus;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.util.Page;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/escalation")
@RequiredArgsConstructor
public class EscalationController {
    private final EscalationQueryService queryService;
    private final EscalationUIMapper mapper;

    @GetMapping
    public Page<EscalationUI> list(
        @RequestParam(defaultValue = "0") Long page,
        @RequestParam(defaultValue = "10") Long pageSize,
        @RequestParam(defaultValue = "") List<EscalationId> ids,
        @RequestParam(required = false) TicketId ticketId,
        @RequestParam(required = false) LocalDate dateFrom,
        @RequestParam(required = false) LocalDate dateTo,
        @RequestParam(required = false) EscalationStatus status,
        @RequestParam(required = false) String team
    ) {
        return queryService.findByQuery(
            EscalationQuery.builder()
                .page(page)
                .pageSize(pageSize)
                .ids(ImmutableList.copyOf(ids))
                .ticketIds(ticketId != null
                    ? ImmutableList.of(ticketId)
                    : ImmutableList.of())
                .dateFrom(dateFrom)
                .dateTo(dateTo)
                .status(status)
                .team(team)
                .build()
        ).map(mapper::mapToUI);
    }
}
