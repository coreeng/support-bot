package com.coreeng.supportbot.stats;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;

@Getter
@RequiredArgsConstructor
public enum StatsType {
    ticketTimeline("ticket-timeline"),
    ticketsAmount("ticket-amount"),
    ticketGeneral("ticket-general"),
    ticketSentimentsCount("ticket-sentiments-count"),
    ticketRating("ticket-ratings");

    @JsonValue
    private final String label;

    @Nullable
    public static StatsType fromLabelOrNull(String str) {
        for (StatsType value : values()) {
            if (value.label().equals(str)) {
                return value;
            }
        }
        return null;
    }
}
