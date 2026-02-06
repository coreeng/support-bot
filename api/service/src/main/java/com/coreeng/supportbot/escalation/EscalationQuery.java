package com.coreeng.supportbot.escalation;

import com.coreeng.supportbot.ticket.TicketId;
import com.google.common.collect.ImmutableList;
import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

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
    @Builder.Default
    private ImmutableList<TicketId> ticketIds = ImmutableList.of();
    private @Nullable TicketId ticketId;
    private @Nullable LocalDate dateFrom;
    private @Nullable LocalDate dateTo;
    private @Nullable EscalationStatus status;
    private @Nullable String team;
    private boolean unlimited;
}
