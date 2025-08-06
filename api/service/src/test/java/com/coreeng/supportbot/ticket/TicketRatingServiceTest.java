package com.coreeng.supportbot.ticket;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketRatingServiceTest {

    @Mock
    private TicketRatingRepository repository;

    @InjectMocks
    private TicketRatingService service;

    private TicketRating sampleRating;
    private UUID sampleRatingId;

    @BeforeEach
    void setUp() {
        sampleRatingId = UUID.randomUUID();
        sampleRating = TicketRating.createNew(
                4,
                "1640995200",
                "closed",
                "production blocking",
                "ingress",
                false
        );
    }

    @Test
    void shouldCreateRating() {
        // Given
        when(repository.insertRating(any(TicketRating.class))).thenReturn(sampleRatingId);

        // When
        UUID result = service.createRating(sampleRating);

        // Then
        assertThat(result).isEqualTo(sampleRatingId);
        verify(repository).insertRating(sampleRating);
    }

    @Test
    void shouldFindRatingById() {
        // Given
        when(repository.findById(sampleRatingId)).thenReturn(sampleRating);

        // When
        TicketRating result = service.findById(sampleRatingId);

        // Then
        assertThat(result).isEqualTo(sampleRating);
        verify(repository).findById(sampleRatingId);
    }

    @Test
    void shouldReturnNullWhenRatingNotFound() {
        // Given
        UUID nonExistentId = UUID.randomUUID();
        when(repository.findById(nonExistentId)).thenReturn(null);

        // When
        TicketRating result = service.findById(nonExistentId);

        // Then
        assertThat(result).isNull();
        verify(repository).findById(nonExistentId);
    }

    @Test
    void shouldFindRatingsByStatus() {
        // Given
        String status = "closed";
        ImmutableList<TicketRating> expectedRatings = ImmutableList.of(sampleRating);
        when(repository.findRatingsByStatus(status)).thenReturn(expectedRatings);

        // When
        ImmutableList<TicketRating> result = service.findRatingsByStatus(status);

        // Then
        assertThat(result).isEqualTo(expectedRatings);
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(sampleRating);
        verify(repository).findRatingsByStatus(status);
    }

    @Test
    void shouldReturnEmptyListWhenNoRatingsFoundByStatus() {
        // Given
        String status = "non-existent";
        ImmutableList<TicketRating> emptyList = ImmutableList.of();
        when(repository.findRatingsByStatus(status)).thenReturn(emptyList);

        // When
        ImmutableList<TicketRating> result = service.findRatingsByStatus(status);

        // Then
        assertThat(result).isEmpty();
        verify(repository).findRatingsByStatus(status);
    }

    @Test
    void shouldFindRatingsByTag() {
        // Given
        String tagCode = "ingress";
        ImmutableList<TicketRating> expectedRatings = ImmutableList.of(sampleRating);
        when(repository.findRatingsByTag(tagCode)).thenReturn(expectedRatings);

        // When
        ImmutableList<TicketRating> result = service.findRatingsByTag(tagCode);

        // Then
        assertThat(result).isEqualTo(expectedRatings);
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(sampleRating);
        verify(repository).findRatingsByTag(tagCode);
    }

    @Test
    void shouldReturnEmptyListWhenNoRatingsFoundByTag() {
        // Given
        String tagCode = "non-existent-tag";
        ImmutableList<TicketRating> emptyList = ImmutableList.of();
        when(repository.findRatingsByTag(tagCode)).thenReturn(emptyList);

        // When
        ImmutableList<TicketRating> result = service.findRatingsByTag(tagCode);

        // Then
        assertThat(result).isEmpty();
        verify(repository).findRatingsByTag(tagCode);
    }

    @Test
    void shouldFindEscalatedRatings() {
        // Given
        TicketRating escalatedRating = TicketRating.createNew(
                1,
                "1640995200",
                "open",
                "production blocking",
                "database",
                true // escalated
        );
        ImmutableList<TicketRating> expectedRatings = ImmutableList.of(escalatedRating);
        when(repository.findEscalatedRatings()).thenReturn(expectedRatings);

        // When
        ImmutableList<TicketRating> result = service.findEscalatedRatings();

        // Then
        assertThat(result).isEqualTo(expectedRatings);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).escalated()).isTrue();
        verify(repository).findEscalatedRatings();
    }

    @Test
    void shouldReturnEmptyListWhenNoEscalatedRatings() {
        // Given
        ImmutableList<TicketRating> emptyList = ImmutableList.of();
        when(repository.findEscalatedRatings()).thenReturn(emptyList);

        // When
        ImmutableList<TicketRating> result = service.findEscalatedRatings();

        // Then
        assertThat(result).isEmpty();
        verify(repository).findEscalatedRatings();
    }

    @Test
    void shouldHandleMultipleRatingsInResults() {
        // Given
        TicketRating rating1 = TicketRating.createNew(5, "1640995200", "closed", "low", "api", false);
        TicketRating rating2 = TicketRating.createNew(3, "1640995300", "closed", "medium", "ui", false);
        TicketRating rating3 = TicketRating.createNew(2, "1640995400", "closed", "high", "database", false);
        
        String status = "closed";
        ImmutableList<TicketRating> expectedRatings = ImmutableList.of(rating1, rating2, rating3);
        when(repository.findRatingsByStatus(status)).thenReturn(expectedRatings);

        // When
        ImmutableList<TicketRating> result = service.findRatingsByStatus(status);

        // Then
        assertThat(result).hasSize(3);
        assertThat(result).containsExactly(rating1, rating2, rating3);
        verify(repository).findRatingsByStatus(status);
    }
}
