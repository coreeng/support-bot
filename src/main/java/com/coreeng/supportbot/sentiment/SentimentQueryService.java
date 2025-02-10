package com.coreeng.supportbot.sentiment;

import com.coreeng.supportbot.ticket.TicketId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;

@Service
@RequiredArgsConstructor
public class SentimentQueryService {
    private final SentimentRepository repository;

    @Nullable
    public TicketSentimentResults findByTicketId(TicketId ticketId) {
        return repository.findByTicketId(ticketId);
    }
}
