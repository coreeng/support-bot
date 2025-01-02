package com.coreeng.supportbot.ticket;

import lombok.Getter;

import java.util.regex.Pattern;

@Getter
public enum TicketOperation {
    toggle("ticket-toggle"),
    summaryView("ticket-summary-view"),
    summarySubmit("ticket-summary-submit");

    public static final Pattern namePattern = Pattern.compile("^ticket-.*$");

    private final String publicName;
    TicketOperation(String publicName) {
        this.publicName = publicName;
    }

    public static TicketOperation fromStringOrNull(String name) {
        for (TicketOperation op : values()) {
            if (op.publicName().equals(name)) {
                return op;
            }
        }
        return null;
    }
}
