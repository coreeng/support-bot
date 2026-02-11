package com.coreeng.supportbot.ticket;

import java.util.regex.Pattern;
import lombok.Getter;
import org.jspecify.annotations.Nullable;

@Getter
public enum TicketOperation {
    summaryView("ticket-summary-view"),
    escalate("ticket-escalate");

    public static final Pattern NAME_PATTERN = Pattern.compile("^ticket-.*$");

    private final String actionId;

    TicketOperation(String actionId) {
        this.actionId = actionId;
    }

    @Nullable public static TicketOperation fromActionIdOrNull(String name) {
        for (TicketOperation op : values()) {
            if (op.actionId().equals(name)) {
                return op;
            }
        }
        return null;
    }
}
