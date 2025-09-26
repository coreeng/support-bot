package com.coreeng.supportbot.ticket;

public enum TicketField {
    status("change-status"),
    tags("change-tags"),
    impact("change-impact"),
    team("change-team"),
    documentation_required("add-or-update-docs");

    private final String actionId;
    TicketField(String actionId) {
        this.actionId = actionId;
    }

    public String actionId() {
        return actionId;
    }
}
