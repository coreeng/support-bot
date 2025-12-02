package com.coreeng.supportbot.rating;

import com.coreeng.supportbot.escalation.EscalationQueryService;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketRepository;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RatingService {
    private final RatingRepository repository;
    private final TicketRepository ticketRepository;
    private final EscalationQueryService escalationQueryService;

    @Transactional
    @Nullable
    public Rating save(TicketId ticketId, int rating) {
        log.info("Attempt to submit rating for ticket {}", ticketId);

        if (ticketRepository.isTicketRated(ticketId)) {
            log.info("Ticket {} has already been rated - ignoring duplicate", ticketId);
            return null;
        }

        // Fetch ticket details for impact and tags
        Ticket ticket = ticketRepository.findTicketById(ticketId);
        if (ticket == null) {
            log.warn("Ticket {} for rating not found", ticketId);
            return null;
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
        ticketRepository.markTicketAsRated(ticketId);

        log.info("Successfully recorded rating for ticket {}", ticketId);
        return ratingRecord.toBuilder()
            .id(ratingId)
            .build();
    }
    public ImmutableList<Rating> getAllRatings() {
        return repository.getAllRatings();
    }
}
