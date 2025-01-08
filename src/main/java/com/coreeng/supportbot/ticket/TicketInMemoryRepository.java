package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.slack.MessageTs;
import com.google.common.collect.ImmutableList;
import org.springframework.stereotype.Repository;

import javax.annotation.Nullable;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

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
}
