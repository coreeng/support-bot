package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.EnumerationValue;
import com.coreeng.supportbot.slack.MessageTs;
import com.google.common.collect.ImmutableList;
import org.springframework.stereotype.Repository;

import javax.annotation.Nullable;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Collections.reverseOrder;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toSet;

@Repository
public class TicketInMemoryRepository implements TicketRepository {
    private final Set<MessageTs> queries = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<TicketId, Ticket> tickets = new ConcurrentHashMap<>();
    private final Map<MessageTs, Ticket> ticketsByQuery = new ConcurrentHashMap<>();
    private final AtomicLong ticketIdSequence = new AtomicLong(1);

    @Override
    public boolean createQueryIfNotExists(MessageTs messageTs) {
        checkNotNull(messageTs);
        return queries.add(messageTs);
    }

    @Override
    public boolean queryExists(MessageTs messageTs) {
        checkNotNull(messageTs);
        return queries.contains(messageTs);
    }

    @Override
    public Ticket createTicketIfNotExists(Ticket ticket) {
        checkNotNull(ticket);
        checkArgument(ticket.id() == null);

        return ticketsByQuery.computeIfAbsent(ticket.queryTs(), queryTs -> {
            long nextId = ticketIdSequence.getAndIncrement();
            Ticket newTicket = ticket.withId(new TicketId(nextId));
            createQueryIfNotExists(ticket.queryTs());
            tickets.put(newTicket.id(), newTicket);
            return newTicket;
        });
    }

    @Override
    public Ticket updateTicket(Ticket updatedTicket) {
        checkNotNull(updatedTicket);
        checkNotNull(updatedTicket.id());

        return ticketsByQuery.computeIfPresent(updatedTicket.queryTs(), (key, t) -> {
            tickets.put(updatedTicket.id(), updatedTicket);
            return updatedTicket;
        });
    }

    @Nullable
    @Override
    public Ticket findTicketById(TicketId ticketId) {
        checkNotNull(ticketId);
        return tickets.get(ticketId);
    }

    @Nullable
    @Override
    public Ticket findTicketByQuery(MessageTs messageTs) {
        checkNotNull(messageTs);
        return ticketsByQuery.get(messageTs);
    }

    @Override
    public Ticket insertStatusLog(Ticket ticket) {
        return tickets.computeIfPresent(ticket.id(), (id, t) -> {
            Ticket newTicket = t.toBuilder()
                .statusHistory(
                    ImmutableList.<Ticket.StatusLog>builderWithExpectedSize(t.statusHistory().size() + 1)
                        .addAll(t.statusHistory())
                        .add(new Ticket.StatusLog(t.status(), Instant.now()))
                        .build()
                )
                .build();
            ticketsByQuery.put(ticket.queryTs(), newTicket);
            return newTicket;
        });
    }

    @Override
    public TicketsPage findTickets(TicketsQuery query) {
        checkNotNull(query);
        checkArgument(query.page() >= 0);
        checkArgument(query.pageSize() > 0);

        Comparator<Ticket> order = switch (query.order()) {
            case asc -> comparing(t -> t.queryTs().getDate());
            case desc -> reverseOrder(comparing(t -> t.queryTs().getDate()));
        };
        ImmutableList<Ticket> queryResult = tickets.values().stream()
            .filter(t -> filterTicket(t, query))
            .sorted(order)
            .collect(toImmutableList());
        int fromIndex = query.page() * query.pageSize();
        int toIndex = Math.min(queryResult.size(), (query.page() + 1) * query.pageSize());
        return new TicketsPage(
            queryResult.subList(fromIndex, toIndex),
            query.page(),
            queryResult.size() / query.pageSize() + 1,
            queryResult.size()
        );
    }

    private boolean filterTicket(Ticket ticket, TicketsQuery query) {
        if (query.status() != null && !query.status().equals(ticket.status())) {
            return false;
        }
        Instant date = ticket.queryTs().getDate();
        if (query.dateFrom() != null && date.isBefore(date)) {
            return false;
        }
        if (query.dateTo() != null && date.isAfter(date)) {
            return false;
        }
        if (!query.tags().isEmpty()
            && !ticket.tags().stream()
            .map(EnumerationValue::code)
            .collect(toSet()).containsAll(query.tags())) {
            return false;
        }
        return query.impact() == null || ticket.impact() == null
            || query.impact().equals(ticket.impact().code());
    }
}
