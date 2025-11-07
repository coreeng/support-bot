package com.coreeng.supportbot.stats;

import com.coreeng.supportbot.rating.rest.RatingUI;
import com.coreeng.supportbot.sentiment.TicketSentimentCountPerDate;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.google.common.collect.ImmutableList;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDate;

@Getter
@SuperBuilder
public class StatsResult {
    @Getter
    @SuperBuilder
    @Jacksonized
    public static class TicketTimeline extends StatsResult {
        @JsonUnwrapped
        private StatsRequest.TicketTimeline request;
        private ImmutableList<DatedValue<Long>> values;
    }


    @Getter
    @SuperBuilder
    @Jacksonized
    public static class TicketAmount extends StatsResult {
        @JsonUnwrapped
        private StatsRequest.TicketAmount request;
        private ImmutableList<CategorisedValue> values;
    }


    @Getter
    @SuperBuilder
    @Jacksonized
    public static class TicketGeneral extends StatsResult {
        @JsonUnwrapped
        private StatsRequest.TicketGeneral request;
        private double avgResponseTimeSecs;
        private double avgResolutionTimeSecs;
        private double largestActiveTicketSecs;
        private long totalEscalations;
    }

    @Getter
    @SuperBuilder
    @Jacksonized
    public static class TicketSentimentCounts extends StatsResult {
        @JsonUnwrapped
        private StatsRequest.TicketSentimentCounts request;
        private ImmutableList<TicketSentimentCountPerDate> values;
    }

    @Getter
    @SuperBuilder
    @Jacksonized
    public static class Ratings extends StatsResult {
        @JsonUnwrapped
        private StatsRequest.Ratings request;
        private ImmutableList<RatingUI> values;
    }

    public record DatedValue<T>(
        LocalDate date,
        T value
    ) {
    }

    public record CategorisedValue(
        String category,
        long value
    ) {
    }
}
