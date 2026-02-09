package com.coreeng.supportbot.homepage;

import com.coreeng.supportbot.slack.UIOption;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.coreeng.supportbot.ticket.TicketsQuery;
import com.google.common.collect.ImmutableList;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import org.jspecify.annotations.Nullable;

@Getter
@Builder(toBuilder = true)
@EqualsAndHashCode
@Jacksonized
public class HomepageFilter {
    @Nullable
    private final TicketStatus status;
    @Builder.Default
    private final TicketsQuery.Order order = TicketsQuery.Order.desc;
    @Nullable
    private final Timeframe timeframe;
    @Builder.Default
    private final ImmutableList<String> tags = ImmutableList.of();
    @Builder.Default
    private final boolean includeNoTags = false;
    @Nullable
    private final String impact;
    @Nullable
    private final String escalationTeam;
    @Nullable
    private final String assignedTo;

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
