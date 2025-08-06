package com.coreeng.supportbot.rating.handler;

import com.coreeng.supportbot.rating.TicketRating;
import com.coreeng.supportbot.rating.TicketRatingService;
import com.coreeng.supportbot.rbac.RbacService;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.SlackBlockActionHandler;
import com.coreeng.supportbot.slack.client.SimpleSlackMessage;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackPostEphemeralMessageRequest;
import com.coreeng.supportbot.rbac.RbacRestrictionMessage;
import com.slack.api.app_backend.interactive_components.payload.BlockActionPayload;
import com.slack.api.bolt.context.builtin.ActionContext;
import com.slack.api.bolt.request.builtin.BlockActionRequest;
import com.slack.api.methods.SlackApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class TicketRatingActionHandler implements SlackBlockActionHandler {
    private final TicketRatingService ticketRatingService;
    private final SlackClient slackClient;

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
                String ticketId = parts[2];
                int rating = Integer.parseInt(parts[3]);
                
                log.info("User {} submitted rating {} for ticket {}", userId, rating, ticketId);
                
                // Handle the rating submission directly
                UUID ratingId = handleRating(ticketId, rating);
                
                // Send confirmation message
                slackClient.postEphemeralMessage(SlackPostEphemeralMessageRequest.builder()
                    .message(SimpleSlackMessage.builder()
                        .text("Thank you for your rating! Your feedback has been recorded.")
                        .build())
                    .channel(payload.getChannel().getId())
                    .threadTs(MessageTs.ofOrNull(payload.getMessage().getThreadTs()))
                    .userId(userId)
                    .build()
                );
                
            } else {
                log.warn("Invalid rating action format: {}", action.getActionId());
            }
        } catch (Exception e) {
            log.error("Error handling rating submission", e);
            slackClient.postEphemeralMessage(SlackPostEphemeralMessageRequest.builder()
                .message(SimpleSlackMessage.builder()
                    .text("Sorry, there was an error recording your rating. Please try again.")
                    .build())
                .channel(payload.getChannel().getId())
                .threadTs(MessageTs.ofOrNull(payload.getMessage().getThreadTs()))
                .userId(userId)
                .build()
            );
        }
    }

    // Combined rating handling logic
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
