package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.escalation.Escalation;
import com.coreeng.supportbot.escalation.EscalationQueryService;
import com.coreeng.supportbot.util.Page;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Nullable;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TicketQueryService {
    private final TicketRepository repository;
    private final EscalationQueryService escalationQueryService;

    public Page<Ticket> findByQuery(TicketsQuery query) {
        return repository.listTickets(query);
    }

    public Page<DetailedTicket> findDetailedTicketByQuery(TicketsQuery query) {
        return repository.listDetailedTickets(query);
    }

    @Nullable
    public Ticket findById(TicketId id) {
        return repository.findTicketById(id);
    }

    @Nullable
    public DetailedTicket findDetailedById(TicketId id) {
        DetailedTicket ticket = repository.findDetailedById(id);
        if (ticket == null) {
            return null;
        }
        ImmutableList<Escalation> escalations = escalationQueryService.listByTicketId(id);
        return new DetailedTicket(ticket.ticket(), escalations);
    }
}
