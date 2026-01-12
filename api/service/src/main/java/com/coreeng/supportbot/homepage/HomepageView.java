package com.coreeng.supportbot.homepage;

import com.coreeng.supportbot.ticket.TicketsQuery;
import com.google.common.collect.ImmutableList;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Builder
public class HomepageView {
    private ImmutableList<TicketView> tickets;
    private long page;
    private long totalPages;
    private long totalTickets;
    private String channelId;

    private Instant timestamp;

    private State state;

    @Getter
    @Builder(toBuilder = true)
    @EqualsAndHashCode
    @Jacksonized
    public static class State {
        private long page;
        private HomepageFilter filter;

        public static State getDefault() {
            return State.builder()
                .page(0)
                .filter(HomepageFilter.builder().build())
                .build();
        }

        public TicketsQuery toTicketsQuery() {
            LocalDate dateFrom;
            LocalDate dateTo;
            switch (filter.timeframe()) {
                case thisWeek -> {
                    dateFrom = LocalDate.now().with(java.time.DayOfWeek.MONDAY);
                    dateTo = LocalDate.now().with(java.time.DayOfWeek.SUNDAY);
                }
                case previousWeek -> {
                    dateFrom = LocalDate.now().with(java.time.DayOfWeek.MONDAY).minusWeeks(1);
                    dateTo = LocalDate.now().with(java.time.DayOfWeek.SUNDAY).minusWeeks(1);
                }
                case null -> {
                    dateFrom = null;
                    dateTo = null;
                }
            }
            return TicketsQuery.builder()
                .page(page)
                .status(filter.status())
                .order(filter.order())
                .dateFrom(dateFrom)
                .dateTo(dateTo)
                .tags(filter.tags())
                .includeNoTags(filter.includeNoTags())
                .escalationTeam(filter.escalationTeam())
                .impacts(
                    filter.impact() != null
                        ? ImmutableList.of(filter.impact())
                        : ImmutableList.of()
                )
                .build();
        }
    }
}
