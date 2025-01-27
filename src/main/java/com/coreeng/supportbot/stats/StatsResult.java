package com.coreeng.supportbot.stats;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.google.common.collect.ImmutableList;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDate;

@Getter
@SuperBuilder(toBuilder = true)
public class StatsResult {
    @Getter
    @SuperBuilder(toBuilder = true)
    @Jacksonized
    public static class TicketTimeline extends StatsResult {
        @JsonUnwrapped
        private StatsRequest.TicketTimeline request;
        private ImmutableList<DatedValue> values;
    }


    @Getter
    @SuperBuilder(toBuilder = true)
    @Jacksonized
    public static class TicketAmount extends StatsResult {
        @JsonUnwrapped
        private StatsRequest.TicketAmount request;
        private ImmutableList<CategorisedValue> values;
    }


    @Getter
    @SuperBuilder(toBuilder = true)
    @Jacksonized
    public static class TicketGeneral extends StatsResult {
        @JsonUnwrapped
        private StatsRequest.TicketGeneral request;
        private double avgResponseTimeSecs;
        private double avgResolutionTimeSecs;
        private double largestActiveTicketSecs;
    }

    public record DatedValue(
        LocalDate date,
        long value
    ) {
    }

    public record CategorisedValue(
        String category,
        long value
    ) {
    }
}
