package com.coreeng.supportbot.rating.rest;

import com.coreeng.supportbot.rating.Rating;
import com.coreeng.supportbot.rating.RatingService;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RatingControllerTest {

    @Mock private RatingService ratingService;
    @Mock private RatingUIMapper mapper;

    @InjectMocks private RatingController controller;

    @Test
    void shouldReturnMappedRatings() {
        // given
        Rating rating1 = mock(Rating.class);
        Rating rating2 = mock(Rating.class);

        RatingUI ui1 = new RatingUI("Alice", 5, new String[]{"fast", "helpful"});
        RatingUI ui2 = new RatingUI("Bob", 3, new String[]{"slow"});

        when(ratingService.findRatingsByStatus("closed"))
                .thenReturn(ImmutableList.of(rating1, rating2));
        when(mapper.mapToUI(rating1)).thenReturn(ui1);
        when(mapper.mapToUI(rating2)).thenReturn(ui2);

        // when
        ResponseEntity<ImmutableList<RatingUI>> response = controller.list();

        // then
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody()).containsExactly(ui1, ui2);

        verify(ratingService).findRatingsByStatus("closed");
        verify(mapper).mapToUI(rating1);
        verify(mapper).mapToUI(rating2);
        verifyNoMoreInteractions(ratingService, mapper);
    }

    @Test
    void shouldReturnEmptyListIfNoRatings() {
        // given
        when(ratingService.findRatingsByStatus("closed")).thenReturn(ImmutableList.of());

        // when
        ResponseEntity<ImmutableList<RatingUI>> response = controller.list();

        // then
        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody()).isEmpty();

        verify(ratingService).findRatingsByStatus("closed");
        verifyNoMoreInteractions(ratingService, mapper);
    }
}
