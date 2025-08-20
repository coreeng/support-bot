package com.coreeng.supportbot.rating.handler;

import com.coreeng.supportbot.rating.Rating;
import com.coreeng.supportbot.rating.RatingService;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.SlackBlockActionHandler;
import com.coreeng.supportbot.slack.client.SimpleSlackMessage;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackPostEphemeralMessageRequest;
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
                
                // Validate rating value
                if (!isValidRating(rating)) {
                    log.warn("Invalid rating value {} submitted by user {} for ticket {}", rating, userId, ticketIdStr);
                    return;
                }
                
                TicketId ticketId = new TicketId(Long.parseLong(ticketIdStr));
                log.info("User {} submitted rating {} for ticket {}", userId, rating, ticketId);
                
                // Check if ticket has already been rated
                if (ticketRepository.isTicketRated(ticketId)) {
                    log.info("Ticket {} has already been rated - ignoring duplicate", ticketIdStr);
                    return;
                }
                
                // Fetch ticket details for impact and tags
                Ticket ticket = ticketRepository.findTicketById(ticketId);
                
                String impact = ticket != null ? ticket.impact() : null;
                String[] tags = ticket != null && ticket.tags() != null ? 
                    ticket.tags().toArray(new String[0]) : null;
                
                // Determine if ticket has any unresolved escalations
                boolean isEscalated = escalationQueryService.countNotResolvedByTicketId(ticketId) > 0;
                
                // Create and submit the rating
                Rating ratingRecord = Rating.createNew(
                    rating,
                    String.valueOf(Instant.now().getEpochSecond()),
                    TicketStatus.closed.name(),
                    impact,
                    tags,
                    isEscalated
                );
                
                UUID ratingId = ratingService.createRating(ratingRecord);
                ticketRepository.markTicketAsRated(ticketId);
                
                log.info("Successfully recorded rating {} for ticket {} with ratingId {}", rating, ticketIdStr, ratingId);
                
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
                if (log.isWarnEnabled()) {
                    log.warn("Invalid rating action format: {}", action.getActionId());
                }
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

    private boolean isValidRating(int rating) {
        return rating >= 1 && rating <= 5;
    }
}
