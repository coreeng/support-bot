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
    private int page = 0;
    @Builder.Default
    private int pageSize = 10;
    @Nullable
    private TicketStatus status;
    // by timestamp
    @Builder.Default
    private Order order = Order.desc;
    @Nullable
    private LocalDate dateFrom;
    @Nullable
    private LocalDate dateTo;
    @Builder.Default
    private ImmutableList<String> tags = ImmutableList.of();
    @Nullable
    private String impact;

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
