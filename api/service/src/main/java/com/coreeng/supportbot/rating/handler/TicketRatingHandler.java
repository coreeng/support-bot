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
        
        // Create anonymous identifier for this rating
        String anonymousId = createAnonymousId(ticketId, "system");
        
        // Create new rating
        TicketRating ticketRating = TicketRating.createNew(
                rating,
                timestamp,
                ticketStatus,
                anonymousId,
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
    
    private String createAnonymousId(String ticketId, String userId) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            String combined = ticketId + ":" + userId;
            byte[] hash = digest.digest(combined.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            
            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            // Fallback to simple hash if SHA-256 is not available
            return String.valueOf((ticketId + ":" + userId).hashCode());
        }
    }
}
