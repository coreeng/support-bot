package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.escalation.Escalation;
import com.coreeng.supportbot.escalation.EscalationQuery;
import com.coreeng.supportbot.escalation.EscalationQueryService;
import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.util.Page;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Nullable;

import static com.google.common.collect.ImmutableList.toImmutableList;

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
        Page<Ticket> ticketsPage = repository.listTickets(query);

        ImmutableList<TicketId> ticketIds = ticketsPage.content().stream()
                .map(Ticket::id)
                .collect(toImmutableList());

        Page<Escalation> escalationsPage = escalationQueryService.findByQuery(
                EscalationQuery.builder()
                        .ticketIds(ticketIds)
                        .unlimited(true)
                        .build()
        );

        Multimap<TicketId, Escalation> escalationsByTicket = Multimaps.index(
                escalationsPage.content(),
                Escalation::ticketId
        );

        ImmutableList<DetailedTicket> detailedTickets = ticketsPage.content().stream()
                .map(ticket -> new DetailedTicket(
                        ticket,
                        ImmutableList.copyOf(escalationsByTicket.get(ticket.id()))
                ))
                .collect(toImmutableList());

        return new Page<>(
                detailedTickets,
                ticketsPage.page(),
                ticketsPage.totalPages(),
                ticketsPage.totalElements()
        );
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

    public boolean queryExists(MessageRef queryRef) {
        return repository.queryExists(queryRef);
    }
}
