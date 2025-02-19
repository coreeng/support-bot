package com.coreeng.supportbot.escalation;

import com.coreeng.supportbot.ticket.TicketId;
import com.google.common.collect.ImmutableList;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder(toBuilder = true)
public class EscalationQuery {
    @Builder.Default
    private long page = 0;
    @Builder.Default
    private long pageSize = 10;
    @Builder.Default
    private ImmutableList<EscalationId> ids = ImmutableList.of();
    private TicketId ticketId;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private EscalationStatus status;
    private String team;
}
