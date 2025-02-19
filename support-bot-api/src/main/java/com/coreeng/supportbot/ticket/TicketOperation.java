package com.coreeng.supportbot.ticket;

import lombok.Getter;

import java.util.regex.Pattern;

@Getter
public enum TicketOperation {
    summaryView("ticket-summary-view"),
    escalate("ticket-escalate");

    public static final Pattern namePattern = Pattern.compile("^ticket-.*$");

    private final String actionId;
    TicketOperation(String actionId) {
        this.actionId = actionId;
    }

    public static TicketOperation fromActionIdOrNull(String name) {
        for (TicketOperation op : values()) {
            if (op.actionId().equals(name)) {
                return op;
            }
        }
        return null;
    }
}
