package com.coreeng.supportbot.stats;

import com.coreeng.supportbot.sentiment.TicketSentimentCountPerDate;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.google.common.collect.ImmutableList;
import java.time.LocalDate;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import org.jspecify.annotations.Nullable;

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

        private RatingsValues values;
        private ImmutableList<WeeklyRating> weekly;
    }

    @Getter
    @SuperBuilder
    @Jacksonized
    public static class WeeklyRating {
        private LocalDate weekStart;
        private Double average;
        private Integer count;
    }

    @Getter
    @SuperBuilder
    @Jacksonized
    public static class RatingsValues {
        @Nullable private Double average;

        private Integer count;
    }

    public record DatedValue<T>(LocalDate date, T value) {}

    public record CategorisedValue(String category, long value) {}
}
