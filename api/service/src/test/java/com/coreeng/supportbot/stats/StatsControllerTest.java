package com.coreeng.supportbot.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.coreeng.supportbot.stats.rest.StatsController;
import com.google.common.collect.ImmutableList;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StatsControllerTest {

    private StatsService statsService;
    private StatsController controller;

    @BeforeEach
    void setUp() {
        statsService = mock(StatsService.class);
        controller = new StatsController(statsService);
    }

    @Test
    void shouldReturnStatsResultsForDifferentRequestTypes() {
        // given
        StatsRequest.TicketTimeline timelineRequest = StatsRequest.TicketTimeline.builder()
                .from(LocalDate.of(2024, 1, 1))
                .to(LocalDate.of(2024, 1, 31))
                .metric(StatsRequest.TicketTimeline.Metric.opened)
                .build();

        StatsRequest.Ratings ratingsRequest = StatsRequest.Ratings.builder()
                .from(LocalDate.of(2024, 1, 1))
                .to(LocalDate.of(2024, 12, 31))
                .build();

        StatsRequest.TicketAmount amountRequest = StatsRequest.TicketAmount.builder()
                .from(LocalDate.of(2024, 2, 1))
                .to(LocalDate.of(2024, 2, 28))
                .groupBy(StatsRequest.TicketAmount.GroupBy.status)
                .build();

        StatsResult.RatingsValues ratingsValues =
                StatsResult.RatingsValues.builder().average(3.75).count(42).build();

        StatsResult.Ratings ratingsResult = StatsResult.Ratings.builder()
                .request(ratingsRequest)
                .values(ratingsValues)
                .build();

        StatsResult.TicketTimeline timelineResult = StatsResult.TicketTimeline.builder()
                .request(timelineRequest)
                .values(ImmutableList.of(
                        new StatsResult.DatedValue<>(LocalDate.of(2024, 1, 1), 5L),
                        new StatsResult.DatedValue<>(LocalDate.of(2024, 1, 2), 10L)))
                .build();

        StatsResult.TicketAmount amountResult = StatsResult.TicketAmount.builder()
                .request(amountRequest)
                .values(ImmutableList.of(
                        new StatsResult.CategorisedValue("open", 12), new StatsResult.CategorisedValue("closed", 7)))
                .build();

        when(statsService.calculate(timelineRequest)).thenReturn(timelineResult);
        when(statsService.calculate(amountRequest)).thenReturn(amountResult);
        when(statsService.calculate(ratingsRequest)).thenReturn(ratingsResult);

        // when
        List<StatsResult> results = controller.stats(List.of(timelineRequest, amountRequest, ratingsRequest));

        // then
        assertThat(results).containsExactly(timelineResult, amountResult, ratingsResult);

        verify(statsService).calculate(timelineRequest);
        verify(statsService).calculate(amountRequest);
        verify(statsService).calculate(ratingsRequest);
        verifyNoMoreInteractions(statsService);
    }
}
