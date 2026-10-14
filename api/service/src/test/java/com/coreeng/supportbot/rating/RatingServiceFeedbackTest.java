package com.coreeng.supportbot.rating;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.coreeng.supportbot.escalation.EscalationQueryService;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketInMemoryRepository;
import com.coreeng.supportbot.ticket.TicketStatus;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RatingServiceFeedbackTest {
    private static final TicketId TICKET_ID = new TicketId(42);

    private RatingInMemoryRepository ratingRepository;

    @Mock
    private EscalationQueryService escalationQueryService;

    private RatingService service;
    private UUID ratingId;

    @BeforeEach
    void setUp() {
        ratingRepository = new RatingInMemoryRepository();
        TicketInMemoryRepository ticketRepository =
                new TicketInMemoryRepository(escalationQueryService, ZoneId.of("UTC"));
        service = new RatingService(ratingRepository, ticketRepository, escalationQueryService);
        ratingId = ratingRepository.insertRating(Rating.builder()
                .rating(2)
                .submittedTs("1704067200")
                .status(TicketStatus.closed)
                .build());
    }

    @Test
    void anonymous_feedback_is_not_linked_to_ticket() {
        boolean saved = service.submitFeedback(submission("  Took too long  ", false));

        assertThat(saved).isTrue();
        RatingFeedback feedback = requireNonNull(ratingRepository.findFeedbackById(ratingId));
        assertThat(feedback.feedback()).isEqualTo("Took too long");
        assertThat(feedback.ticketId()).isNull();
    }

    @Test
    void named_feedback_is_linked_to_ticket() {
        service.submitFeedback(submission("Great help", true));

        RatingFeedback feedback = requireNonNull(ratingRepository.findFeedbackById(ratingId));
        assertThat(feedback.ticketId()).isEqualTo(TICKET_ID);
    }

    @Test
    void rejects_second_submission_for_same_rating() {
        assertThat(service.submitFeedback(submission("first", false))).isTrue();

        assertThat(service.submitFeedback(submission("second", true))).isFalse();
        RatingFeedback feedback = requireNonNull(ratingRepository.findFeedbackById(ratingId));
        assertThat(feedback.feedback()).isEqualTo("first");
        assertThat(feedback.ticketId()).isNull();
    }

    @Test
    void rejects_feedback_for_unknown_rating() {
        RatingFeedbackSubmission unknown = new RatingFeedbackSubmission(UUID.randomUUID(), TICKET_ID, "text", false);

        assertThat(service.submitFeedback(unknown)).isFalse();
    }

    @Test
    void rejects_blank_feedback() {
        assertThatThrownBy(() -> service.submitFeedback(submission("   ", false)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ratingRepository.findFeedbackById(ratingId)).isNull();
    }

    @Test
    void rejects_too_long_feedback() {
        String tooLong = "x".repeat(RatingService.FEEDBACK_MAX_LENGTH + 1);

        assertThatThrownBy(() -> service.submitFeedback(submission(tooLong, false)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getFeedback_exposes_week_bucket_and_ticket() {
        service.submitFeedback(submission("Great help", true));

        // 1704067200 is Monday 2024-01-01 UTC
        assertThat(service.getFeedback(null, null))
                .containsExactly(new RatingFeedbackItem(2, LocalDate.of(2024, 1, 1), "Great help", TICKET_ID));
    }

    @Test
    void getFeedback_widens_date_range_to_whole_weeks_latest_week_first() {
        // 2024-01-29 and 2024-03-04 are Mondays, so the range covers 2024-01-29 .. 2024-03-03
        submitFeedbackOn(LocalDate.of(2024, 1, 28), "before");
        submitFeedbackOn(LocalDate.of(2024, 1, 29), "first week");
        submitFeedbackOn(LocalDate.of(2024, 3, 3), "last week");
        submitFeedbackOn(LocalDate.of(2024, 3, 4), "after");

        assertThat(service.getFeedback(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29)))
                .extracting(RatingFeedbackItem::feedback)
                .containsExactly("last week", "first week");
    }

    @Test
    void getFeedback_single_day_returns_whole_week_without_revealing_submission_order() {
        // Monday 2024-02-05 .. Sunday 2024-02-11; submitted in reverse of the expected order
        submitFeedbackOn(LocalDate.of(2024, 2, 11), 2, "b");
        submitFeedbackOn(LocalDate.of(2024, 2, 9), 5, "a");
        submitFeedbackOn(LocalDate.of(2024, 2, 7), 2, "a");
        submitFeedbackOn(LocalDate.of(2024, 2, 12), 2, "next week");

        assertThat(service.getFeedback(LocalDate.of(2024, 2, 7), LocalDate.of(2024, 2, 7)))
                .extracting(RatingFeedbackItem::rating, RatingFeedbackItem::feedback)
                .containsExactly(tuple(2, "a"), tuple(2, "b"), tuple(5, "a"));
    }

    private void submitFeedbackOn(LocalDate date, String feedback) {
        submitFeedbackOn(date, 4, feedback);
    }

    private void submitFeedbackOn(LocalDate date, int rating, String feedback) {
        UUID id = ratingRepository.insertRating(Rating.builder()
                .rating(rating)
                .submittedTs(String.valueOf(date.atStartOfDay(ZoneOffset.UTC).toEpochSecond()))
                .status(TicketStatus.closed)
                .build());
        service.submitFeedback(new RatingFeedbackSubmission(id, TICKET_ID, feedback, false));
    }

    private RatingFeedbackSubmission submission(String feedback, boolean shareName) {
        return new RatingFeedbackSubmission(ratingId, TICKET_ID, feedback, shareName);
    }
}
