package com.coreeng.supportbot.stats;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StatsType {
    ticketTimeline("ticket-timeline"),
    ticketsAmount("ticket-amount"),
    ticketGeneral("ticket-general");

    @JsonValue
    private final String label;

    public static StatsType fromLabelOrNull(String str) {
        for (StatsType value : values()) {
            if (value.label().equals(str)) {
                return value;
            }
        }
        return null;
    }
}
