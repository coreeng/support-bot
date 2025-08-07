package com.coreeng.supportbot.rating;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class RatingTest {

    @Test
    void shouldCreateNewRating() {
        // When
        Rating rating = Rating.createNew(
                5,
                "1640995200",
                "closed",
                "anonymous123",
                "production blocking",
                new String[]{"ingress"},
                true
        );

        // Then
        assertThat(rating.rating()).isEqualTo(5);
        assertThat(rating.submittedTs()).isEqualTo("1640995200");
        assertThat(rating.status()).isEqualTo("closed");
        assertThat(rating.anonymousId()).isEqualTo("anonymous123");
        assertThat(rating.impact()).isEqualTo("production blocking");
        assertThat(rating.tags()).isEqualTo(new String[]{"ingress"});
        assertThat(rating.isEscalated()).isTrue();
        assertThat(rating.id()).isNull();
    }

    @Test
    void shouldValidateValidRatings() {
        Rating rating1 = createRatingWithValue(1);
        Rating rating2 = createRatingWithValue(3);
        Rating rating5 = createRatingWithValue(5);

        assertThat(rating1.isValidRating()).isTrue();
        assertThat(rating2.isValidRating()).isTrue();
        assertThat(rating5.isValidRating()).isTrue();
    }

    @Test
    void shouldValidateInvalidRatings() {
        Rating rating0 = createRatingWithValue(0);
        Rating rating6 = createRatingWithValue(6);
        Rating ratingNegative = createRatingWithValue(-1);

        assertThat(rating0.isValidRating()).isFalse();
        assertThat(rating6.isValidRating()).isFalse();
        assertThat(ratingNegative.isValidRating()).isFalse();
    }

    @Test
    void shouldIdentifyHighRatings() {
        Rating rating4 = createRatingWithValue(4);
        Rating rating5 = createRatingWithValue(5);
        Rating rating3 = createRatingWithValue(3);

        assertThat(rating4.isHighRating()).isTrue();
        assertThat(rating5.isHighRating()).isTrue();
        assertThat(rating3.isHighRating()).isFalse();
    }

    @Test
    void shouldIdentifyLowRatings() {
        Rating rating1 = createRatingWithValue(1);
        Rating rating2 = createRatingWithValue(2);
        Rating rating3 = createRatingWithValue(3);

        assertThat(rating1.isLowRating()).isTrue();
        assertThat(rating2.isLowRating()).isTrue();
        assertThat(rating3.isLowRating()).isFalse();
    }

    private Rating createRatingWithValue(int rating) {
        return Rating.createNew(
                rating,
                "1640995200",
                "closed",
                "anonymous123",
                "medium",
                new String[]{"test"},
                false
        );
    }
}