package com.coreeng.supportbot.stats;

import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketRepository;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.coreeng.supportbot.ticket.TicketsQuery;
import com.coreeng.supportbot.util.Page;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

@Component
@RequiredArgsConstructor
public class TicketsTimelineCollector implements StatsCollector<StatsRequest.TicketTimeline> {
    private final TicketRepository repository;
    private final ZoneId timezone;

    @Override
    public StatsType getSupportedType() {
        return StatsType.ticketTimeline;
    }

    @Override
    public StatsResult calculateResults(StatsRequest.TicketTimeline request) {
        Page<Ticket> tickets = repository.listTickets(TicketsQuery.builder()
            .unlimited(true)
            .dateFrom(request.from())
            .dateTo(request.to())
            .build());
        ImmutableList<StatsResult.DatedValue<Long>> values = switch (request.metric()) {
            case opened -> countOpenedTickets(tickets);
            case active -> countActiveTickets(tickets);
        };
        return StatsResult.TicketTimeline.builder()
            .request(request)
            .values(values)
            .build();
    }

    private ImmutableList<StatsResult.DatedValue<Long>> countOpenedTickets(Page<Ticket> tickets) {
        ZoneOffset offset = timezone.getRules().getOffset(Instant.now());
        return tickets.content().stream()
            .collect(groupingBy(
                t -> t.statusLog().getFirst().date().atOffset(offset).toLocalDate(),
                counting()
            )).entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> new StatsResult.DatedValue<>(e.getKey(), e.getValue()))
            .collect(toImmutableList());
    }

    // TODO: not correct algorithm, need to fix it later
    private ImmutableList<StatsResult.DatedValue<Long>> countActiveTickets(Page<Ticket> tickets) {
        ZoneOffset offset = timezone.getRules().getOffset(Instant.now());
        return tickets.content().stream()
            .filter(t -> t.status() != TicketStatus.closed)
            .collect(groupingBy(
                t -> t.statusLog().getFirst().date().atOffset(offset).toLocalDate(),
                counting()
            )).entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> new StatsResult.DatedValue<>(e.getKey(), e.getValue()))
            .collect(toImmutableList());
    }
}
