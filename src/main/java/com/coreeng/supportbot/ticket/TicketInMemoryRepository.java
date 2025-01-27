package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.config.EnumerationValue;
import com.coreeng.supportbot.escalation.EscalationQueryService;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.util.Page;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Repository;

import javax.annotation.Nullable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.chrono.ChronoLocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Collections.reverseOrder;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toSet;

@Repository
@RequiredArgsConstructor
public class TicketInMemoryRepository implements TicketRepository {
    private final EscalationQueryService escalationQueryService;
    private final ZoneId timezone;

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

    @Nullable
    @Override
    public DetailedTicket findDetailedById(TicketId id) {
        Ticket ticket = findTicketById(id);
        if (ticket == null) {
            return null;
        }
        return mapToDetailed(ticket);
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
    public Page<Ticket> findTickets(TicketsQuery query) {
        return findTicketsAndMap(query, Function.identity());
    }

    @Override
    public Page<DetailedTicket> findDetailedTickets(TicketsQuery query) {
        return findTicketsAndMap(query, this::mapToDetailed);
    }

    @NotNull
    private <X> Page<X> findTicketsAndMap(TicketsQuery query, Function<Ticket, X> mapperFn) {
        checkNotNull(query);
        checkArgument(query.page() >= 0);
        checkArgument(query.pageSize() > 0);

        Comparator<Ticket> order = switch (query.order()) {
            case asc -> comparing(t -> t.queryTs().getDate());
            case desc -> reverseOrder(comparing(t -> t.queryTs().getDate()));
        };
        ImmutableList<X> queryResult = tickets.values().stream()
            .filter(t -> filterTicket(t, query))
            .sorted(order)
            .map(mapperFn)
            .collect(toImmutableList());

        ImmutableList<X> elements;
        long page;
        long totalPages;
        if (query.unlimited()) {
            elements = queryResult;
            page = totalPages = 0;
        } else {
            long fromIndex = query.page() * query.pageSize();
            long toIndex = Math.min(queryResult.size(), (query.page() + 1) * query.pageSize());
            elements = queryResult.subList((int) fromIndex, (int) toIndex);
            page = query.page();
            totalPages = queryResult.size() / query.pageSize() + 1;
        }
        return new Page<>(
            elements,
            page, totalPages, queryResult.size()
        );
    }

    private DetailedTicket mapToDetailed(Ticket ticket) {
        return new DetailedTicket(
            ticket,
            escalationQueryService.countNotResolvedByTicketId(ticket.id()) > 0
        );
    }

    private boolean filterTicket(Ticket ticket, TicketsQuery query) {
        if (!query.ids().isEmpty() && !query.ids().contains(ticket.id())) {
            return false;
        }
        if (query.status() != null && !query.status().equals(ticket.status())) {
            return false;
        }
        ZonedDateTime queryDate = ticket.queryTs().getDate().atZone(timezone);
        if (query.dateFrom() != null && query.dateFrom().isAfter(ChronoLocalDate.from(queryDate))) {
            return false;
        }
        if (query.dateTo() != null && query.dateTo().isBefore(ChronoLocalDate.from(queryDate))) {
            return false;
        }
        if (query.escalated() != null) {
            long escalationsCount = escalationQueryService.countNotResolvedByTicketId(ticket.id());
            if (query.escalated()) {
                if (escalationsCount == 0) {
                    return false;
                }
            } else if (escalationsCount > 0) {
                return false;
            }
        }
        if (!query.tags().isEmpty()
            && !ticket.tags().stream()
            .map(EnumerationValue::code)
            .collect(toSet()).containsAll(query.tags())) {
            return false;
        }
        if (!query.impacts().isEmpty()) {
            if (ticket.impact() == null) {
                return false;
            }
            if (!query.impacts().contains(ticket.impact().code())) {
                return false;
            }
        }
        if (!query.teams().isEmpty()) {
            if (ticket.team() == null) {
                return false;
            }
            if (!query.teams().contains(ticket.team())) {
                return false;
            }
        }
        return true;
    }
}
