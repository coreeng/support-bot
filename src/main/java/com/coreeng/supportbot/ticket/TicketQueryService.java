package com.coreeng.supportbot.ticket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketQueryService {
    private final TicketRepository ticketRepository;

    public TicketsPage findByQuery(TicketsQuery query) {
        return ticketRepository.findTickets(query);
    }

    @Nullable
    public Ticket findById(TicketId id) {
        return ticketRepository.findTicketById(id);
    }
}
