package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.EnumerationValue;
import com.coreeng.supportbot.slack.MessageTs;
import com.google.common.collect.ImmutableList;
import com.slack.api.model.block.LayoutBlock;

import javax.annotation.Nullable;

public record TicketSummaryView(
    TicketId ticketId,
    QuerySummaryView query,
    TicketStatus currentStatus,
    ImmutableList<Ticket.StatusLog> statusLogs,
    ImmutableList<EnumerationValue> tags,
    ImmutableList<EnumerationValue> currentTags,
    ImmutableList<EnumerationValue> impacts,
    @Nullable EnumerationValue currentImpact
) {
    public static TicketSummaryView of(
        Ticket ticket,
        QuerySummaryView query,
        ImmutableList<EnumerationValue> tags,
        ImmutableList<EnumerationValue> impacts
    ) {
        return new TicketSummaryView(
            ticket.id(),
            query,
            ticket.status(),
            ticket.statusHistory(),
            tags,
            ticket.tags(),
            impacts,
            ticket.impact()
        );
    }

    public record QuerySummaryView(
        ImmutableList<LayoutBlock> blocks,
        MessageTs messageTs,
        String senderId,
        String permalink
    ) {
    }
}
