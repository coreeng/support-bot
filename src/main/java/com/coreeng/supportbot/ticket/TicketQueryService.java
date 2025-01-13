package com.coreeng.supportbot.ticket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketQueryService {
    private final TicketRepository ticketRepository;

    public TicketsPage findTickets(TicketsQuery query) {
        return ticketRepository.findTickets(query);
    }
}
