package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.escalation.EscalationQueryService;
import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.util.Page;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.chrono.ChronoLocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
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

@RequiredArgsConstructor
public class TicketInMemoryRepository implements TicketRepository {
    private final EscalationQueryService escalationQueryService;
    private final ZoneId timezone;

    private final Set<MessageRef> queries = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<TicketId, Ticket> tickets = new ConcurrentHashMap<>();
    private final Map<MessageRef, Ticket> ticketsByQuery = new ConcurrentHashMap<>();
    private final AtomicLong ticketIdSequence = new AtomicLong(1);

    @Override
    public void createQueryIfNotExists(MessageRef queryRef) {
        checkNotNull(queryRef);
        queries.add(queryRef);
    }

    @Override
    public boolean queryExists(MessageRef queryRef) {
        checkNotNull(queryRef);
        return queries.contains(queryRef);
    }

    @Override
    public boolean deleteQueryIfNoTicket(MessageRef queryRef) {
        checkNotNull(queryRef);
        if (ticketsByQuery.containsKey(queryRef)) {
            return false;
        }
        return queries.remove(queryRef);
    }

    @Override
    public Ticket createTicketIfNotExists(Ticket ticket) {
        checkNotNull(ticket);
        checkArgument(ticket.id() == null);

        MessageRef queryRef = ticket.queryRef();
        return ticketsByQuery.computeIfAbsent(queryRef, queryTs -> {
            long nextId = ticketIdSequence.getAndIncrement();
            Ticket newTicket = ticket.withId(new TicketId(nextId));
            createQueryIfNotExists(queryRef);
            tickets.put(newTicket.id(), newTicket);
            return newTicket;
        });
    }

    @Override
    public Ticket updateTicket(Ticket updatedTicket) {
        checkNotNull(updatedTicket);
        checkNotNull(updatedTicket.id());

        return ticketsByQuery.computeIfPresent(updatedTicket.queryRef(), (key, t) -> {
            Ticket newTicket = updatedTicket.toBuilder()
                .assignedTo(updatedTicket.assignedTo() != null ? updatedTicket.assignedTo() : t.assignedTo())
                .build();
            tickets.put(newTicket.id(), newTicket);
            return newTicket;
        });
    }

    @Override
    public boolean touchTicketById(TicketId id, Instant timestamp) {
        checkNotNull(id);

        return tickets.computeIfPresent(id, (key, t) -> {
            Ticket touchedTicket = t.toBuilder()
                .lastInteractedAt(timestamp)
                .build();
            ticketsByQuery.put(t.queryRef(), touchedTicket);
            return touchedTicket;
        }) != null;
    }

    @Override
    public boolean assign(TicketId ticketId, String slackUserId) {
        return assignInternal(ticketId, slackUserId);
    }

    private boolean assignInternal(TicketId ticketId, String slackUserId) {
        checkNotNull(ticketId);
        checkNotNull(slackUserId);

        return tickets.computeIfPresent(ticketId, (id, t) -> {
            Ticket updated = t.toBuilder()
                .assignedTo(slackUserId)
                .build();
            ticketsByQuery.put(t.queryRef(), updated);
            return updated;
        }) != null;
    }

    @Nullable
    @Override
    public Ticket findTicketById(TicketId ticketId) {
        checkNotNull(ticketId);
        return tickets.get(ticketId);
    }

    @Nullable
    @Override
    public Ticket findTicketByQuery(MessageRef queryRef) {
        checkNotNull(queryRef);
        return ticketsByQuery.get(queryRef);
    }

    @Override
    public Ticket insertStatusLog(Ticket ticket, Instant at) {
        return tickets.computeIfPresent(ticket.id(), (id, t) -> {
            Ticket newTicket = t.toBuilder()
                .statusLog(
                    ImmutableList.<Ticket.StatusLog>builderWithExpectedSize(t.statusLog().size() + 1)
                        .addAll(t.statusLog())
                        .add(new Ticket.StatusLog(t.status(), at))
                        .build()
                )
                .build();
            ticketsByQuery.put(ticket.queryRef(), newTicket);
            return newTicket;
        });
    }

    @Override
    public Page<Ticket> listTickets(TicketsQuery query) {
        return findTicketsAndMap(query, Function.identity());
    }

    @NotNull
    private <X> Page<X> findTicketsAndMap(TicketsQuery query, Function<Ticket, X> mapperFn) {
        checkNotNull(query);
        checkArgument(query.page() >= 0);
        checkArgument(query.pageSize() > 0);

        Comparator<Ticket> order = switch (query.order()) {
            case asc -> comparing(t -> t.queryTs().getDate());
            case desc -> reverseOrder(comparing(t -> t.queryTs().getDate()));
            case null -> comparing(t -> 0);
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
        if (query.includeNoTags() || !query.tags().isEmpty()) {
            boolean matchesNoTags = query.includeNoTags() && ticket.tags().isEmpty();
            boolean matchesSelectedTags = !query.tags().isEmpty()
                && new HashSet<>(ticket.tags()).containsAll(query.tags());
            if (!matchesNoTags && !matchesSelectedTags) {
                return false;
            }
        }
        if (!query.impacts().isEmpty()) {
            if (ticket.impact() == null) {
                return false;
            }
            if (!query.impacts().contains(ticket.impact())) {
                return false;
            }
        }
        if (!query.teams().isEmpty()) {
            if (ticket.team() == null) {
                return false;
            }
            return query.teams().contains(ticket.team().toCode());
        }
        if (query.assignedTo() != null) {
            if (ticket.assignedTo() == null) {
                return false;
            }
            return query.assignedTo().equals(ticket.assignedTo());
        }
        return true;
    }

    @Override
    public ImmutableList<TicketId> listStaleTicketIds(Instant checkAt, Duration timeToStale) {
        Instant stalenessThreshold = checkAt.minus(timeToStale);
        return tickets.values().stream()
            .filter(t -> t.status() == TicketStatus.opened && t.lastInteractedAt().isBefore(stalenessThreshold))
            .map(Ticket::id)
            .collect(toImmutableList());
    }

    @Override
    public ImmutableList<TicketId> listStaleTicketIdsToRemindOf(Instant checkAt, Duration reminderInterval) {
        Instant reminderThreshold = checkAt.minus(reminderInterval);
        return tickets.values().stream()
            .filter(t -> t.status() == TicketStatus.stale && t.lastInteractedAt().isBefore(reminderThreshold))
            .map(Ticket::id)
            .collect(toImmutableList());
    }

    @Override
    public boolean isTicketRated(TicketId ticketId) {
        Ticket ticket = tickets.get(ticketId);
        return ticket != null && ticket.ratingSubmitted();
    }

    @Override
    public void markTicketAsRated(TicketId ticketId) {
        Ticket ticket = tickets.get(ticketId);
        if (ticket != null) {
            tickets.put(ticketId, ticket.toBuilder().ratingSubmitted(true).build());
        }
    }
}
