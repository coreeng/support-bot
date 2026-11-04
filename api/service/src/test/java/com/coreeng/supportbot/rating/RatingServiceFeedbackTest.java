package com.coreeng.supportbot.rating;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.coreeng.supportbot.escalation.EscalationQueryService;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketInMemoryRepository;
import com.coreeng.supportbot.ticket.TicketStatus;
import java.time.Clock;
import java.time.Instant;
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
    // Long after every week used below, so those weeks have all ended
    private static final Instant NOW = Instant.parse("2025-06-01T12:00:00Z");

    private RatingInMemoryRepository ratingRepository;

    @Mock
    private EscalationQueryService escalationQueryService;

    private TicketInMemoryRepository ticketRepository;
    private RatingService service;
    private UUID ratingId;

    @BeforeEach
    void setUp() {
        ratingRepository = new RatingInMemoryRepository();
        ticketRepository = new TicketInMemoryRepository(escalationQueryService, ZoneId.of("UTC"));
        service = serviceAt(NOW);
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
        assertThat(ratingRepository.findFeedbackById(ratingId)).isNull();
    }

    @Test
    void accepts_feedback_at_max_length_after_stripping() {
        String atMax = "x".repeat(RatingService.FEEDBACK_MAX_LENGTH);

        assertThat(service.submitFeedback(submission("  " + atMax + "  ", false)))
                .isTrue();
        assertThat(requireNonNull(ratingRepository.findFeedbackById(ratingId)).feedback())
                .isEqualTo(atMax);
    }

    @Test
    void getFeedback_exposes_week_bucket_and_ticket() {
        service.submitFeedback(submission("Great help", true));

        // 1704067200 is Monday 2024-01-01 UTC
        assertThat(service.getFeedback(null, null))
                .containsExactly(new RatingFeedbackItem(2, LocalDate.of(2024, 1, 1), "Great help", TICKET_ID));
    }

    @Test
    void getFeedback_returns_anonymous_feedback_without_a_ticket() {
        service.submitFeedback(submission("Took too long", false));

        assertThat(service.getFeedback(null, null))
                .containsExactly(new RatingFeedbackItem(2, LocalDate.of(2024, 1, 1), "Took too long", null));
    }

    @Test
    void getFeedback_holds_back_anonymous_feedback_until_its_week_has_ended() {
        // With the clock at Monday 2024-02-12 10:00 UTC the running week starts that day; 2024-02-11 is the Sunday
        // before
        submitFeedbackAt(Instant.parse("2024-02-11T23:59:59Z"), 2, "last week");
        submitFeedbackAt(Instant.parse("2024-02-12T00:00:00Z"), 2, "this week");
        submitFeedbackAt(Instant.parse("2024-02-12T09:00:00Z"), 5, "this week, linked", true);

        assertThat(serviceAt(Instant.parse("2024-02-12T10:00:00Z")).getFeedback(null, null))
                .extracting(RatingFeedbackItem::feedback)
                .containsExactly("this week, linked", "last week");
        assertThat(serviceAt(Instant.parse("2024-02-19T00:00:00Z")).getFeedback(null, null))
                .extracting(RatingFeedbackItem::feedback)
                .containsExactly("this week", "this week, linked", "last week");
    }

    @Test
    void getFeedback_ends_the_week_in_utc_whatever_the_clock_zone() {
        submitFeedbackOn(LocalDate.of(2024, 2, 7), "running week");

        // Sunday 23:00 UTC is already Monday in UTC+2
        Clock aheadOfUtc = Clock.fixed(Instant.parse("2024-02-11T23:00:00Z"), ZoneOffset.ofHours(2));
        RatingService serviceAheadOfUtc =
                new RatingService(ratingRepository, ticketRepository, escalationQueryService, aheadOfUtc);

        assertThat(serviceAheadOfUtc.getFeedback(null, null)).isEmpty();
    }

    @Test
    void getFeedback_holds_back_anonymous_feedback_even_when_range_covers_the_running_week() {
        submitFeedbackOn(LocalDate.of(2024, 2, 14), "running week");
        submitFeedbackAt(
                LocalDate.of(2024, 2, 14).atStartOfDay(ZoneOffset.UTC).toInstant(), 5, "running week, linked", true);

        RatingService serviceInRunningWeek = serviceAt(Instant.parse("2024-02-18T23:59:59Z"));
        assertThat(serviceInRunningWeek.getFeedback(LocalDate.of(2024, 2, 12), LocalDate.of(2024, 2, 18)))
                .extracting(RatingFeedbackItem::feedback)
                .containsExactly("running week, linked");
        assertThat(serviceInRunningWeek.getFeedback(LocalDate.of(2024, 2, 12), null))
                .extracting(RatingFeedbackItem::feedback)
                .containsExactly("running week, linked");
    }

    @Test
    void getFeedback_keeps_linked_feedback_within_the_date_range() {
        // 2024-01-29 and 2024-03-04 are Mondays, so the range covers 2024-01-29 .. 2024-03-03
        submitFeedbackAt(LocalDate.of(2024, 1, 28).atStartOfDay(ZoneOffset.UTC).toInstant(), 4, "before", true);
        submitFeedbackAt(LocalDate.of(2024, 2, 14).atStartOfDay(ZoneOffset.UTC).toInstant(), 4, "inside, linked", true);
        submitFeedbackOn(LocalDate.of(2024, 2, 15), "inside, anonymous");
        submitFeedbackAt(LocalDate.of(2024, 3, 4).atStartOfDay(ZoneOffset.UTC).toInstant(), 4, "after", true);

        assertThat(service.getFeedback(LocalDate.of(2024, 2, 1), LocalDate.of(2024, 2, 29)))
                .extracting(RatingFeedbackItem::feedback)
                .containsExactly("inside, anonymous", "inside, linked");
    }

    @Test
    void getFeedback_reports_the_monday_of_the_week_never_the_submission_day() {
        // Wednesday 2024-02-07 15:37:00 and Sunday 2024-02-11 23:59:59 UTC, both in the week of Monday 2024-02-05
        submitFeedbackAt(Instant.parse("2024-02-07T15:37:00Z"), 2, "mid-week");
        submitFeedbackAt(Instant.parse("2024-02-11T23:59:59Z"), 3, "sunday night");

        assertThat(service.getFeedback(null, null))
                .extracting(RatingFeedbackItem::feedback, RatingFeedbackItem::weekStart)
                .containsExactlyInAnyOrder(
                        tuple("mid-week", LocalDate.of(2024, 2, 5)), tuple("sunday night", LocalDate.of(2024, 2, 5)));
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
        // Monday 2024-02-05 .. Sunday 2024-02-11; submission dates are deliberately out of step with the expected order
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
        submitFeedbackAt(date.atStartOfDay(ZoneOffset.UTC).toInstant(), rating, feedback);
    }

    private void submitFeedbackAt(Instant submittedAt, int rating, String feedback) {
        submitFeedbackAt(submittedAt, rating, feedback, false);
    }

    private void submitFeedbackAt(Instant submittedAt, int rating, String feedback, boolean shareName) {
        UUID id = ratingRepository.insertRating(Rating.builder()
                .rating(rating)
                .submittedTs(String.valueOf(submittedAt.getEpochSecond()))
                .status(TicketStatus.closed)
                .build());
        assertThat(service.submitFeedback(new RatingFeedbackSubmission(id, TICKET_ID, feedback, shareName)))
                .isTrue();
    }

    private RatingService serviceAt(Instant now) {
        return new RatingService(
                ratingRepository, ticketRepository, escalationQueryService, Clock.fixed(now, ZoneOffset.UTC));
    }

    private RatingFeedbackSubmission submission(String feedback, boolean shareName) {
        return new RatingFeedbackSubmission(ratingId, TICKET_ID, feedback, shareName);
    }
}
