package com.coreeng.supportbot.rating;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.coreeng.supportbot.escalation.EscalationQueryService;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketRepository;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.google.common.collect.ImmutableList;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RatingService {
    public static final int FEEDBACK_MAX_LENGTH = 3000;

    private final RatingRepository repository;
    private final TicketRepository ticketRepository;
    private final EscalationQueryService escalationQueryService;

    @Transactional
    public Rating save(TicketId ticketId, int rating) {
        log.info("Attempt to submit rating for ticket {}", ticketId);

        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("rating must be between 1 and 5");
        }
        if (!ticketRepository.tryMarkTicketAsRated(ticketId)) {
            Ticket ticket = ticketRepository.findTicketById(ticketId);
            if (ticket == null) {
                throw new RatingTicketNotFoundException(ticketId);
            }
            if (ticket.status() != TicketStatus.closed) {
                throw new IllegalArgumentException("Ticket must be closed before rating can be submitted");
            }
            throw new IllegalArgumentException("Ticket has already been rated");
        }
        Ticket ticket = ticketRepository.findTicketById(ticketId);
        if (ticket == null) {
            throw new IllegalStateException("Ticket not found after rating claim: " + ticketId.render());
        }

        boolean isEscalated = escalationQueryService.existsByTicketId(ticketId);

        // Create and submit the rating
        Rating ratingRecord = Rating.builder()
                .rating(rating)
                .submittedTs(String.valueOf(Instant.now().getEpochSecond()))
                .status(ticket.status())
                .impact(ticket.impact())
                .tags(ticket.tags())
                .isEscalated(isEscalated)
                .build();

        UUID ratingId = repository.insertRating(ratingRecord);

        log.info("Successfully recorded rating for ticket {}", ticketId);
        return ratingRecord.toBuilder().id(ratingId).build();
    }

    public ImmutableList<Rating> getAllRatings() {
        return repository.getAllRatings();
    }

    /**
     * Attaches follow-up feedback to a previously saved rating.
     *
     * <p>The ticket is only linked when the submitter opted in to sharing their name. Anonymous feedback
     * stays as unlinked as the rating itself, so nothing here may log the ticket together with the rating.
     *
     * @return false if the rating doesn't exist or already has feedback
     */
    @Transactional
    public boolean submitFeedback(RatingFeedbackSubmission submission) {
        String feedback = submission.feedback().strip();
        if (feedback.isEmpty()) {
            throw new IllegalArgumentException("feedback must not be blank");
        }
        if (feedback.length() > FEEDBACK_MAX_LENGTH) {
            throw new IllegalArgumentException("feedback must be at most " + FEEDBACK_MAX_LENGTH + " characters");
        }

        boolean saved = repository.saveFeedback(new RatingFeedback(
                submission.ratingId(), feedback, submission.shareName() ? submission.ticketId() : null));
        if (saved) {
            log.info(
                    "Recorded {} feedback for rating {}",
                    submission.shareName() ? "named" : "anonymous",
                    submission.ratingId());
        } else {
            log.warn(
                    "Ignoring feedback for rating {}: rating missing or feedback already submitted",
                    submission.ratingId());
        }
        return saved;
    }

    /**
     * Feedback submitted within the UTC weeks (Monday to Sunday) covering the given dates, latest week first.
     *
     * <p>The range is widened to whole weeks and items are never ordered by their exact timestamp, so a caller
     * can't narrow anonymous feedback down to a day or a submission order (see ADR-009).
     */
    public ImmutableList<RatingFeedbackItem> getFeedback(@Nullable LocalDate from, @Nullable LocalDate to) {
        long fromEpoch =
                from != null ? weekStartOf(from).atStartOfDay(ZoneOffset.UTC).toEpochSecond() : Long.MIN_VALUE;
        long toEpoch = to != null
                ? weekStartOf(to).plusWeeks(1).atStartOfDay(ZoneOffset.UTC).toEpochSecond()
                : Long.MAX_VALUE;
        return repository.getAllFeedback().stream()
                .filter(f -> {
                    long submitted = Long.parseLong(f.submittedTs());
                    return submitted >= fromEpoch && submitted < toEpoch;
                })
                .map(f -> new RatingFeedbackItem(f.rating(), weekStartOf(f.submittedTs()), f.feedback(), f.ticketId()))
                .sorted(Comparator.comparing(RatingFeedbackItem::weekStart)
                        .reversed()
                        .thenComparingInt(RatingFeedbackItem::rating)
                        .thenComparing(RatingFeedbackItem::feedback))
                .collect(toImmutableList());
    }

    private static LocalDate weekStartOf(String submittedTs) {
        return weekStartOf(Instant.ofEpochSecond(Long.parseLong(submittedTs))
                .atZone(ZoneOffset.UTC)
                .toLocalDate());
    }

    private static LocalDate weekStartOf(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }
}
