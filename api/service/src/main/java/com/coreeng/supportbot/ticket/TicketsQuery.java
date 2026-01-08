package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.slack.UIOption;
import com.google.common.collect.ImmutableList;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

import javax.annotation.Nullable;
import java.time.LocalDate;

@Getter
@Builder(toBuilder = true)
@Jacksonized
public class TicketsQuery {
    @Builder.Default
    private long page = 0;
    @Builder.Default
    private long pageSize = 10;
    private boolean unlimited;
    @Builder.Default
    private ImmutableList<TicketId> ids = ImmutableList.of();
    @Nullable
    private TicketStatus status;
    // by date
    @Builder.Default
    @Nullable
    private Order order = Order.desc;
    @Nullable
    private LocalDate dateFrom;
    @Nullable
    private LocalDate dateTo;
    @Nullable
    private Boolean escalated;
    @Nullable
    private String escalationTeam;
    @Builder.Default
    private ImmutableList<String> tags = ImmutableList.of();
    @Builder.Default
    private ImmutableList<String> impacts = ImmutableList.of();
    @Builder.Default
    private ImmutableList<String> teams = ImmutableList.of();
    @Nullable
    private String assignedTo;

    @Getter
    public enum Order implements UIOption {
        asc("Oldest to Newest (Ascending)"),
        desc("Newest to Oldest (Descending)");

        private final String label;

        Order(String label) {
            this.label = label;
        }

        @Override
        public String value() {
            return name();
        }
    }
}
