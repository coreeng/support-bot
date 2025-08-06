package com.coreeng.supportbot.rating.handler;

import com.coreeng.supportbot.rating.TicketRating;
import com.coreeng.supportbot.rating.TicketRatingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class TicketRatingHandler {

    private final TicketRatingService ticketRatingService;

    public UUID handleRating(String ticketId, int rating, String ticketStatus, String ticketImpact, String[] tags, String[] escalatedTeams) {
        log.info("Handling ticket rating: ticketId={}, rating={}, status={}", ticketId, rating, ticketStatus);
        
        // Validate rating
        if (!isValidRating(rating)) {
            throw new IllegalArgumentException("Rating must be between 1 and 5, but was: " + rating);
        }
        
        // Create timestamp
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        
        // Create new rating
        TicketRating ticketRating = TicketRating.createNew(
                rating,
                timestamp,
                ticketStatus,
                ticketImpact,
                tags,
                escalatedTeams
        );
        
        // Save rating
        UUID ratingId = ticketRatingService.createRating(ticketRating);
        
        boolean isEscalated = ticketRating.isEscalated();
        if (isEscalated) {
            log.warn("Rating for escalated ticket {}: rating={}, escalated teams={}.", 
                    ticketId, rating, escalatedTeams);
        }
        
        log.info("Successfully handled ticket rating: ticketId={}, ratingId={}, escalated={}", 
                ticketId, ratingId, isEscalated);
        
        return ratingId;
    }
    
    public UUID handleRating(String ticketId, int rating) {
        // Simplified version with default values
        return handleRating(ticketId, rating, "unknown", null, null, null);
    }
    
    private boolean isValidRating(int rating) {
        return rating >= 1 && rating <= 5;
    }
}
