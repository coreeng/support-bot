package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.slack.UIOption;
import lombok.Getter;

@Getter
public enum TicketStatus implements UIOption {
    opened("Opened", "large_orange_circle"),
    closed("Closed", "large_green_circle");

    private final String label;
    private final String emoji;

    TicketStatus(String label, String emoji) {
        this.label = label;
        this.emoji = emoji;
    }

    @Override
    public String value() {
        return name();
    }
}
