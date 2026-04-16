package com.coreeng.supportbot.rating;

import com.coreeng.supportbot.escalation.EscalationQueryService;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketRepository;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RatingService {
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
}
