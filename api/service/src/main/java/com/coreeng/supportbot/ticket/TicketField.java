package com.coreeng.supportbot.ticket;

public enum TicketField {
    status("ticket-change-status"),
    tags("ticket-change-tags"),
    impact("ticket-change-impact"),
    team("ticket-change-team"),
    assignee("ticket-change-assignee");

    private final String actionId;
    TicketField(String actionId) {
        this.actionId = actionId;
    }

    public String actionId() {
        return actionId;
    }
}
