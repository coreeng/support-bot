package com.coreeng.supportbot.sentiment;

import com.coreeng.supportbot.ticket.TicketId;
import lombok.RequiredArgsConstructor;

import javax.annotation.Nullable;

@RequiredArgsConstructor
public class SentimentQueryService {
    private final SentimentRepository repository;

    @Nullable
    public TicketSentimentResults findByTicketId(TicketId ticketId) {
        return repository.findByTicketId(ticketId);
    }
}
