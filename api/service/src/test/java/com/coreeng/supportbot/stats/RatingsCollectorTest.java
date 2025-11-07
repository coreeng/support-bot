package com.coreeng.supportbot.stats;

import com.coreeng.supportbot.rating.Rating;
import com.coreeng.supportbot.rating.RatingService;
import com.coreeng.supportbot.ticket.TicketStatus;
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
    void shouldCalculateAverageAndCountForAllRatings_whenNoDatesProvided() {
        // given
        Rating rating1 = createRating(5, "1762273189"); // ~Nov 2025
        Rating rating2 = createRating(3, "1762354035"); // ~Nov 2025
        Rating rating3 = createRating(4, "1762519295"); // ~Nov 2025

        when(ratingService.getAllRatings()).thenReturn(ImmutableList.of(rating1, rating2, rating3));

        StatsRequest.Ratings request = StatsRequest.Ratings.builder().build();

        // when
        StatsResult.Ratings result = (StatsResult.Ratings) collector.calculateResults(request);

        // then
        assertThat(result.values().count()).isEqualTo(3);
        assertThat(result.values().average()).isEqualTo(4.0); // (5 + 3 + 4) / 3
    }

    @Test
    void shouldFilterRatingsByDateRange_whenDatesProvided() {
        // given
        LocalDate nov1 = LocalDate.of(2025, 11, 1);
        LocalDate nov5 = LocalDate.of(2025, 11, 5);

        // Create ratings with specific timestamps using actual epoch seconds
        long nov1Epoch = nov1.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        long nov3Epoch = nov1.plusDays(2).atTime(12, 0).toEpochSecond(ZoneOffset.UTC);
        long nov7Epoch = nov1.plusDays(6).atStartOfDay(ZoneOffset.UTC).toEpochSecond();

        Rating ratingNov1 = createRating(5, String.valueOf(nov1Epoch));
        Rating ratingNov3 = createRating(3, String.valueOf(nov3Epoch));
        Rating ratingNov7 = createRating(4, String.valueOf(nov7Epoch));

        when(ratingService.getAllRatings()).thenReturn(ImmutableList.of(ratingNov1, ratingNov3, ratingNov7));

        StatsRequest.Ratings request = StatsRequest.Ratings.builder()
                .from(nov1)
                .to(nov5)
                .build();

        // when
        StatsResult.Ratings result = (StatsResult.Ratings) collector.calculateResults(request);

        // then - should only include Nov 1 and Nov 3 (Nov 7 is outside range)
        assertThat(result.values().count()).isEqualTo(2);
        assertThat(result.values().average()).isEqualTo(4.0); // (5 + 3) / 2
    }

    @Test
    void shouldReturnNoAverage_whenNoRatings() {
        // given
        when(ratingService.getAllRatings()).thenReturn(ImmutableList.of());

        StatsRequest.Ratings request = StatsRequest.Ratings.builder().build();

        // when
        StatsResult.Ratings result = (StatsResult.Ratings) collector.calculateResults(request);

        // then
        assertThat(result.values().count()).isEqualTo(0);
        assertThat(result.values().average()).isNull();
    }

    @Test
    void shouldReturnNoAverage_whenNoRatingsInDateRange() {
        // given
        LocalDate jan1 = LocalDate.of(2024, 1, 1);
        LocalDate jan31 = LocalDate.of(2024, 1, 31);

        // Create a rating in November 2025 (outside the January 2024 range)
        LocalDate nov2025 = LocalDate.of(2025, 11, 1);
        long nov2025Epoch = nov2025.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        Rating ratingNov = createRating(5, String.valueOf(nov2025Epoch));

        when(ratingService.getAllRatings()).thenReturn(ImmutableList.of(ratingNov));

        StatsRequest.Ratings request = StatsRequest.Ratings.builder()
                .from(jan1)
                .to(jan31)
                .build();

        // when
        StatsResult.Ratings result = (StatsResult.Ratings) collector.calculateResults(request);

        // then
        assertThat(result.values().count()).isEqualTo(0);
        assertThat(result.values().average()).isNull();
    }

    @Test
    void shouldReturnAllRatings_whenOnlyFromDateProvided() {
        // given
        Rating rating1 = createRating(5, "1762273189");
        Rating rating2 = createRating(3, "1762354035");

        when(ratingService.getAllRatings()).thenReturn(ImmutableList.of(rating1, rating2));

        StatsRequest.Ratings request = StatsRequest.Ratings.builder()
                .from(LocalDate.of(2025, 11, 1))
                .build();

        // when
        StatsResult.Ratings result = (StatsResult.Ratings) collector.calculateResults(request);

        // then - should return all ratings since 'to' is null
        assertThat(result.values().count()).isEqualTo(2);
        assertThat(result.values().average()).isEqualTo(4.0);
    }

    @Test
    void shouldReturnAllRatings_whenOnlyToDateProvided() {
        // given
        Rating rating1 = createRating(5, "1762273189");
        Rating rating2 = createRating(3, "1762354035");

        when(ratingService.getAllRatings()).thenReturn(ImmutableList.of(rating1, rating2));

        StatsRequest.Ratings request = StatsRequest.Ratings.builder()
                .to(LocalDate.of(2025, 12, 31))
                .build();

        // when
        StatsResult.Ratings result = (StatsResult.Ratings) collector.calculateResults(request);

        // then - should return all ratings since 'from' is null
        assertThat(result.values().count()).isEqualTo(2);
        assertThat(result.values().average()).isEqualTo(4.0);
    }

    @Test
    void shouldIncludeRatingsOnBoundaryDates() {
        // given
        LocalDate nov1 = LocalDate.of(2025, 11, 1);
        LocalDate nov5 = LocalDate.of(2025, 11, 5);

        // Rating exactly at start of Nov 1
        long nov1Start = nov1.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        // Rating exactly at end of Nov 5 (23:59:59)
        long nov5End = nov5.atTime(23, 59, 59).toEpochSecond(ZoneOffset.UTC);

        Rating ratingAtStart = createRating(5, String.valueOf(nov1Start));
        Rating ratingAtEnd = createRating(3, String.valueOf(nov5End));

        when(ratingService.getAllRatings()).thenReturn(ImmutableList.of(ratingAtStart, ratingAtEnd));

        StatsRequest.Ratings request = StatsRequest.Ratings.builder()
                .from(nov1)
                .to(nov5)
                .build();

        // when
        StatsResult.Ratings result = (StatsResult.Ratings) collector.calculateResults(request);

        // then - both boundary ratings should be included
        assertThat(result.values().count()).isEqualTo(2);
        assertThat(result.values().average()).isEqualTo(4.0);
    }

    private Rating createRating(int ratingValue, String submittedTs) {
        return Rating.builder()
                .rating(ratingValue)
                .submittedTs(submittedTs)
                .status(TicketStatus.closed)
                .impact("productionBlocking")
                .isEscalated(false)
                .build();
    }
}

