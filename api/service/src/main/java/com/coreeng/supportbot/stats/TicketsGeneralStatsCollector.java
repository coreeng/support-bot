package com.coreeng.supportbot.stats;

import com.coreeng.supportbot.ticket.*;
import com.coreeng.supportbot.util.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class TicketsGeneralStatsCollector implements StatsCollector<StatsRequest.TicketGeneral> {
    private final TicketQueryService queryService;

    @Override
    public StatsType getSupportedType() {
        return StatsType.ticketGeneral;
    }

    @Override
    public StatsResult calculateResults(StatsRequest.TicketGeneral request) {
        Page<DetailedTicket> tickets = queryService.findDetailedTicketByQuery(TicketsQuery.builder()
            .unlimited(true)
            .dateFrom(request.from())
            .dateTo(request.to())
            .build());
        double avgResolutionTime = tickets.content().stream()
            .filter(t -> t.ticket().status() == TicketStatus.closed)
            .mapToDouble(t -> Duration.between(
                t.ticket().queryTs().getDate(),
                t.ticket().statusLog().getLast().date()
            ).getSeconds())
            .average()
            .orElse(0.0);

        double avgResponseTime = tickets.content().stream()
            .mapToDouble(t -> Duration.between(
                t.ticket().queryTs().getDate(),
                t.ticket().statusLog().getFirst().date()
            ).getSeconds())
            .average()
            .orElse(0.0);

        double largestActiveTicketSecs = tickets.content().stream()
            .mapToDouble(t -> switch (t.ticket().status()) {
                case closed -> Duration.between(
                    t.ticket().statusLog().getFirst().date(),
                    t.ticket().statusLog().getLast().date()
                ).getSeconds();
                case opened, stale -> Duration.between(
                    t.ticket().statusLog().getFirst().date(),
                    Instant.now()
                ).getSeconds();
            })
            .max()
            .orElse(0.0);

        long escalatedCount = tickets.content().stream().filter(DetailedTicket::escalated).count();

        return StatsResult.TicketGeneral.builder()
            .request(request)
            .avgResolutionTimeSecs(avgResolutionTime)
            .avgResponseTimeSecs(avgResponseTime)
            .largestActiveTicketSecs(largestActiveTicketSecs)
            .totalEscalations(escalatedCount)
            .build();
    }
}
