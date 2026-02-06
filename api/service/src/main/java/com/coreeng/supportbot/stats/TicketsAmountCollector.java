package com.coreeng.supportbot.stats;

import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketRepository;
import com.coreeng.supportbot.ticket.TicketsQuery;
import com.coreeng.supportbot.util.Page;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.function.Function;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

@Component
@RequiredArgsConstructor
public class TicketsAmountCollector implements StatsCollector<StatsRequest.TicketAmount> {
    private final TicketRepository repository;

    @Override
    public StatsType getSupportedType() {
        return StatsType.ticketsAmount;
    }

    @Override
    public StatsResult calculateResults(StatsRequest.TicketAmount request) {
        Page<Ticket> tickets = repository.listTickets(TicketsQuery.builder()
            .unlimited(true)
            .dateFrom(request.from())
            .dateTo(request.to())
            .build());
        Function<Ticket, String> groupByKeyMapper = switch (request.groupBy()) {
            case impact -> t -> Objects.requireNonNullElse(t.impact(), "unknown");
            case status -> t -> t.status().name();
        };
        ImmutableList<StatsResult.CategorisedValue> values = tickets.content().stream()
            .collect(groupingBy(
                groupByKeyMapper,
                counting()
            )).entrySet().stream()
            .map(e -> new StatsResult.CategorisedValue(e.getKey(), e.getValue()))
            .collect(toImmutableList());
        return StatsResult.TicketAmount.builder()
            .request(request)
            .values(values)
            .build();
    }
}
