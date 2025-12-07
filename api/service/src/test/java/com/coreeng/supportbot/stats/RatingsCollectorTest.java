package com.coreeng.supportbot.stats;

import com.coreeng.supportbot.rating.Rating;
import com.coreeng.supportbot.rating.RatingService;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RatingsCollectorTest {

    @Mock
    private RatingService ratingService;

    private RatingsCollector collector;

    @BeforeEach
    void setUp() {
        collector = new RatingsCollector(ratingService);
    }

    @Test
    void shouldReturnTicketRatingAsType() {
        assertThat(collector.getSupportedType()).isEqualTo(StatsType.ticketRating);
    }

    @Test
    void calculateResults_returnsAverageCountAndWeeklyBuckets() {
        Rating r1 = rating(3, LocalDate.of(2024, 1, 1)); // Monday week1
        Rating r2 = rating(5, LocalDate.of(2024, 1, 3)); // same week1
        Rating r3 = rating(2, LocalDate.of(2024, 1, 8)); // Monday week2

        when(ratingService.getAllRatings()).thenReturn(ImmutableList.of(r1, r2, r3));

        StatsRequest.Ratings req = StatsRequest.Ratings.builder()
            .from(LocalDate.of(2024, 1, 1))
            .to(LocalDate.of(2024, 1, 31))
            .build();

        StatsResult.Ratings result = (StatsResult.Ratings) collector.calculateResults(req);

        assertThat(result.values().count()).isEqualTo(3);
        assertThat(result.values().average()).isEqualTo((3 + 5 + 2) / 3.0);

        assertThat(result.weekly()).hasSize(2);
        StatsResult.WeeklyRating week1 = result.weekly().get(0);
        assertThat(week1.weekStart()).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(week1.count()).isEqualTo(2);
        assertThat(week1.average()).isEqualTo((3 + 5) / 2.0);

        StatsResult.WeeklyRating week2 = result.weekly().get(1);
        assertThat(week2.weekStart()).isEqualTo(LocalDate.of(2024, 1, 8));
        assertThat(week2.count()).isEqualTo(1);
        assertThat(week2.average()).isEqualTo(2.0);
    }

    @Test
    void calculateResults_filtersByFromToInclusive() {
        Rating before = rating(1, LocalDate.of(2023, 12, 31));
        Rating inRange = rating(4, LocalDate.of(2024, 1, 2));
        Rating after = rating(5, LocalDate.of(2024, 2, 1));

        when(ratingService.getAllRatings()).thenReturn(ImmutableList.of(before, inRange, after));

        StatsRequest.Ratings req = StatsRequest.Ratings.builder()
            .from(LocalDate.of(2024, 1, 1))
            .to(LocalDate.of(2024, 1, 31))
            .build();

        StatsResult.Ratings result = (StatsResult.Ratings) collector.calculateResults(req);

        assertThat(result.values().count()).isEqualTo(1);
        assertThat(result.values().average()).isEqualTo(4.0);
        assertThat(result.weekly()).hasSize(1);
        assertThat(result.weekly().get(0).count()).isEqualTo(1);
    }

    @Test
    void calculateResults_noRatings_returnsNullAverageAndEmptyWeekly() {
        when(ratingService.getAllRatings()).thenReturn(ImmutableList.of());

        StatsRequest.Ratings req = StatsRequest.Ratings.builder().build();

        StatsResult.Ratings result = (StatsResult.Ratings) collector.calculateResults(req);

        assertThat(result.values().count()).isEqualTo(0);
        assertThat(result.values().average()).isNull();
        assertThat(result.weekly()).isEmpty();
    }

    private Rating rating(int value, LocalDate date) {
        long epoch = date.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        return Rating.builder()
            .rating(value)
            .submittedTs(Long.toString(epoch))
            .build();
    }
}

