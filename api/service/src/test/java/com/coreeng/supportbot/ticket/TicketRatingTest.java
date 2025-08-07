package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.rating.TicketRating;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TicketRatingTest {

    @Test
    void shouldCreateNewRating() {
        // When
        TicketRating rating = TicketRating.createNew(
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
        TicketRating rating1 = createRatingWithValue(1);
        TicketRating rating2 = createRatingWithValue(3);
        TicketRating rating5 = createRatingWithValue(5);

        assertThat(rating1.isValidRating()).isTrue();
        assertThat(rating2.isValidRating()).isTrue();
        assertThat(rating5.isValidRating()).isTrue();
    }

    @Test
    void shouldValidateInvalidRatings() {
        TicketRating rating0 = createRatingWithValue(0);
        TicketRating rating6 = createRatingWithValue(6);
        TicketRating ratingNegative = createRatingWithValue(-1);

        assertThat(rating0.isValidRating()).isFalse();
        assertThat(rating6.isValidRating()).isFalse();
        assertThat(ratingNegative.isValidRating()).isFalse();
    }

    @Test
    void shouldIdentifyHighRatings() {
        TicketRating rating4 = createRatingWithValue(4);
        TicketRating rating5 = createRatingWithValue(5);
        TicketRating rating3 = createRatingWithValue(3);

        assertThat(rating4.isHighRating()).isTrue();
        assertThat(rating5.isHighRating()).isTrue();
        assertThat(rating3.isHighRating()).isFalse();
    }

    @Test
    void shouldIdentifyLowRatings() {
        TicketRating rating1 = createRatingWithValue(1);
        TicketRating rating2 = createRatingWithValue(2);
        TicketRating rating3 = createRatingWithValue(3);

        assertThat(rating1.isLowRating()).isTrue();
        assertThat(rating2.isLowRating()).isTrue();
        assertThat(rating3.isLowRating()).isFalse();
    }

    private TicketRating createRatingWithValue(int rating) {
        return TicketRating.createNew(
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