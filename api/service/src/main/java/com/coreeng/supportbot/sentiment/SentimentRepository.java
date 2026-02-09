package com.coreeng.supportbot.sentiment;

import com.coreeng.supportbot.ticket.TicketId;
import com.google.common.collect.ImmutableList;

import org.jspecify.annotations.Nullable;
import java.time.LocalDate;

public interface SentimentRepository {
    void save(TicketId ticketId, TicketSentimentResults sentiment);

    ImmutableList<TicketId> listNotAnalysedClosedTickets();

    @Nullable
    TicketSentimentResults findByTicketId(TicketId ticketId);
    ImmutableList<TicketSentimentCountPerDate> countBetweenDates(LocalDate from, LocalDate to);
}
