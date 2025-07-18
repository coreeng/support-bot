package com.coreeng.supportbot.homepage;

import com.coreeng.supportbot.slack.UIOption;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.coreeng.supportbot.ticket.TicketsQuery;
import com.google.common.collect.ImmutableList;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import javax.annotation.Nullable;

@Getter
@Builder(toBuilder = true)
@EqualsAndHashCode
@Jacksonized
public class HomepageFilter {
    @Nullable
    private TicketStatus status;
    @Builder.Default
    private TicketsQuery.Order order = TicketsQuery.Order.desc;
    @Nullable
    private Timeframe timeframe;
    @Builder.Default
    private ImmutableList<String> tags = ImmutableList.of();
    @Nullable
    private String impact;
    @Nullable
    private String escalationTeam;

    @Getter
    public enum Timeframe implements UIOption {
        thisWeek("This Week"),
        previousWeek("Previous Week");

        private final String label;

        Timeframe(String label) {
            this.label = label;
        }

        @Override
        public String value() {
            return name();
        }
    }
}
