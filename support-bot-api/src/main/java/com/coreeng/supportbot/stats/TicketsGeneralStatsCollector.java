package com.coreeng.supportbot.stats;

import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketRepository;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.coreeng.supportbot.ticket.TicketsQuery;
import com.coreeng.supportbot.util.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class TicketsGeneralStatsCollector implements StatsCollector<StatsRequest.TicketGeneral> {
    private final TicketRepository repository;

    @Override
    public StatsType getSupportedType() {
        return StatsType.ticketGeneral;
    }

    @Override
    public StatsResult calculateResults(StatsRequest.TicketGeneral request) {
        Page<Ticket> tickets = repository.listTickets(TicketsQuery.builder()
            .unlimited(true)
            .dateFrom(request.from())
            .dateTo(request.to())
            .build());

        double avgResolutionTime = tickets.content().stream()
            .filter(t -> t.status() == TicketStatus.closed)
            .mapToDouble(t -> Duration.between(
                t.queryTs().getDate(),
                t.statusLog().getLast().date()
            ).getSeconds())
            .average()
            .orElse(0.0);

        double avgResponseTime = tickets.content().stream()
            .mapToDouble(t -> Duration.between(
                t.queryTs().getDate(),
                t.statusLog().getFirst().date()
            ).getSeconds())
            .average()
            .orElse(0.0);

        double largestActiveTicketSecs = tickets.content().stream()
            .mapToDouble(t -> switch (t.status()) {
                case closed -> Duration.between(
                    t.statusLog().getFirst().date(),
                    t.statusLog().getLast().date()
                ).getSeconds();
                case opened, stale -> Duration.between(
                    t.statusLog().getFirst().date(),
                    Instant.now()
                ).getSeconds();
            })
            .max()
            .orElse(0.0);

        return StatsResult.TicketGeneral.builder()
            .request(request)
            .avgResolutionTimeSecs(avgResolutionTime)
            .avgResponseTimeSecs(avgResponseTime)
            .largestActiveTicketSecs(largestActiveTicketSecs)
            .build();
    }
}
