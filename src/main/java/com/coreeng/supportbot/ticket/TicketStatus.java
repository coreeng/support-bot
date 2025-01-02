package com.coreeng.supportbot.ticket;

public enum TicketStatus {
    unresolved,
    resolved;

    public String renderMessage(String dateString) {
        return switch (this) {
            case unresolved -> "Opened: " + dateString;
            case resolved -> "Closed: " + dateString;
        };
    }
}
