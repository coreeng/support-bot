package com.coreeng.supportbot.ticket;

public enum TicketField {
    status("change-status"),
    tags("change-tags"),
    impact("change-impact"),;

    private final String actionId;
    TicketField(String actionId) {
        this.actionId = actionId;
    }

    public String actionId() {
        return actionId;
    }
}
