package com.coreeng.supportbot.rating.handler;

import com.coreeng.supportbot.rating.Rating;
import com.coreeng.supportbot.rating.RatingService;
import com.coreeng.supportbot.rbac.RbacService;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.SlackBlockActionHandler;
import com.coreeng.supportbot.slack.client.SimpleSlackMessage;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackPostEphemeralMessageRequest;
import com.coreeng.supportbot.rbac.RbacRestrictionMessage;
import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketRepository;
import com.coreeng.supportbot.ticket.TicketStatus;
import com.coreeng.supportbot.escalation.EscalationQueryService;
import com.slack.api.app_backend.interactive_components.payload.BlockActionPayload;
import com.slack.api.bolt.context.builtin.ActionContext;
import com.slack.api.bolt.request.builtin.BlockActionRequest;
import com.slack.api.methods.SlackApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class RatingActionHandler implements SlackBlockActionHandler {
    private final RatingService ratingService;
    private final SlackClient slackClient;
    private final TicketRepository ticketRepository;
    private final EscalationQueryService escalationQueryService;

    @Override
    public Pattern getPattern() {
        // Pattern to match rating action IDs like "rating_submit_ticketId_rating"
        return Pattern.compile("rating_.*");
    }

    @Override
    public void apply(BlockActionRequest req, ActionContext context) throws IOException, SlackApiException {
        BlockActionPayload payload = req.getPayload();
        String userId = payload.getUser().getId();

        for (BlockActionPayload.Action action : payload.getActions()) {
            String actionId = action.getActionId();
            
            if (actionId.startsWith("rating_submit_")) {
                handleRatingSubmission(action, payload, userId);
            } else {
                log.atWarn()
                    .addArgument(actionId)
                    .log("Unknown rating action: {}");
            }
        }
    }

    private void handleRatingSubmission(BlockActionPayload.Action action, BlockActionPayload payload, String userId) {
        try {
            // Parse action ID format: "rating_submit_{ticketId}_{rating}"
            String[] parts = action.getActionId().split("_");
            if (parts.length >= 4) {
                String ticketIdStr = parts[2];
                int rating = Integer.parseInt(parts[3]);
                
                log.info("User {} submitted rating {} for ticket {}", userId, rating, ticketIdStr);
                
                // Create anonymous identifier to prevent duplicates while maintaining anonymity
                String anonymousId = createAnonymousId(ticketIdStr, userId);
                
                // Check if user has already rated this ticket
                if (ratingService.hasAlreadyRated(anonymousId)) {
                    log.info("User {} already submitted a rating for ticket {} - ignoring duplicate", userId, ticketIdStr);
                    return;
                }
                
                // Fetch ticket details for impact and tags
                TicketId ticketId = new TicketId(Integer.parseInt(ticketIdStr));
                Ticket ticket = ticketRepository.findTicketById(ticketId);
                
                String impact = ticket != null ? ticket.impact() : null;
                String[] tags = ticket != null && ticket.tags() != null ? 
                    ticket.tags().toArray(new String[0]) : null;
                
                // Determine if ticket has any unresolved escalations
                boolean isEscalated = escalationQueryService.countNotResolvedByTicketId(ticketId) > 0;
                
                // Handle the rating submission
                UUID ratingId = handleRating(rating, anonymousId, TicketStatus.closed.name(), impact, tags, isEscalated);
                
                // Send confirmation message in the same thread as the rating request
                MessageTs threadTs = null;
                if (payload.getContainer() != null && payload.getContainer().getThreadTs() != null) {
                    threadTs = MessageTs.of(payload.getContainer().getThreadTs());
                } else if (payload.getMessage() != null && payload.getMessage().getThreadTs() != null) {
                    threadTs = MessageTs.of(payload.getMessage().getThreadTs());
                }
                
                slackClient.postEphemeralMessage(SlackPostEphemeralMessageRequest.builder()
                    .message(SimpleSlackMessage.builder()
                        .text(String.format("Thank you for your %d star rating! Your feedback has been recorded.", rating))
                        .build())
                    .channel(payload.getChannel().getId())
                    .threadTs(threadTs)
                    .userId(userId)
                    .build()
                );
                
            } else {
                log.warn("Invalid rating action format: {}", action.getActionId());
            }
        } catch (Exception e) {
            log.error("Error handling rating submission", e);
            
            MessageTs threadTs = null;
            if (payload.getContainer() != null && payload.getContainer().getThreadTs() != null) {
                threadTs = MessageTs.of(payload.getContainer().getThreadTs());
            } else if (payload.getMessage() != null && payload.getMessage().getThreadTs() != null) {
                threadTs = MessageTs.of(payload.getMessage().getThreadTs());
            }
            
            slackClient.postEphemeralMessage(SlackPostEphemeralMessageRequest.builder()
                .message(SimpleSlackMessage.builder()
                    .text("Sorry, there was an error recording your rating. Please try again.")
                    .build())
                .channel(payload.getChannel().getId())
                .threadTs(threadTs)
                .userId(userId)
                .build()
            );
        }
    }

    // Combined rating handling logic
    public UUID handleRating(int rating, String anonymousId, String ticketStatus, String ticketImpact, String[] tags, boolean isEscalated) {
        log.info("Handling ticket rating: anonymousId={}, rating={}, status={}", anonymousId, rating, ticketStatus);
        
        // Validate rating
        if (!isValidRating(rating)) {
            throw new IllegalArgumentException("Rating must be between 1 and 5, but was: " + rating);
        }
        
        // Create timestamp
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        
        // Create new rating
        Rating ratingRecord = Rating.createNew(
                rating,
                timestamp,
                ticketStatus,
                anonymousId,
                ticketImpact,
                tags,
                isEscalated
        );
        
        // Save rating
        UUID ratingId = ratingService.createRating(ratingRecord);
        
        if (isEscalated) {
            log.warn("Rating for escalated ticket: rating={}.", rating);
        }
        
        log.info("Successfully handled ticket rating: anonymousId={}, ratingId={}, escalated={}", 
                anonymousId, ratingId, isEscalated);
        
        return ratingId;
    }
    
    public UUID handleRating(String ticketId, int rating) {
        // Simplified version with default values - ticket must be closed to submit rating
        // Note: This method should not be used for user submissions as it doesn't prevent duplicates
        String anonymousId = createAnonymousId(ticketId, "system");
        return handleRating(rating, anonymousId, TicketStatus.closed.name(), null, null, false);
    }
    
    private String createAnonymousId(String ticketId, String userId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String combined = ticketId + ":" + userId;
            byte[] hash = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
            
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
        } catch (NoSuchAlgorithmException e) {
            // Fallback to simple hash if SHA-256 is not available
            return String.valueOf((ticketId + ":" + userId).hashCode());
        }
    }
    
    private boolean isValidRating(int rating) {
        return rating >= 1 && rating <= 5;
    }
}
