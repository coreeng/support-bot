package com.coreeng.supportbot.rating.handler;

import com.coreeng.supportbot.rating.Rating;
import com.coreeng.supportbot.rating.RatingService;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.SlackBlockActionHandler;
import com.coreeng.supportbot.slack.client.SimpleSlackMessage;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackGetMessageByTsRequest;
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
import com.slack.api.model.Message;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class RatingActionHandler implements SlackBlockActionHandler {
    private static final String ratingSubmitPrefix = "rating_submit_";
    
    private final RatingService ratingService;
    private final SlackClient slackClient;
    private final TicketRepository ticketRepository;
    private final EscalationQueryService escalationQueryService;

    @Override
    public Pattern getPattern() {
        return Pattern.compile(Pattern.quote(ratingSubmitPrefix) + ".*");
    }

    @Override
    public void apply(BlockActionRequest req, ActionContext context) throws IOException, SlackApiException {
        BlockActionPayload payload = req.getPayload();

        for (BlockActionPayload.Action action : payload.getActions()) {
            handleRatingSubmission(action, payload);
        }
    }

    @Transactional
    private void handleRatingSubmission(BlockActionPayload.Action action, BlockActionPayload payload) {
        try {
            String actionId = action.getActionId();
            String remainder = actionId.substring(ratingSubmitPrefix.length());
            String[] parts = remainder.split("_");
            
            if (parts.length >= 2) {
                String ticketIdStr = parts[0];
                String ratingStr = parts[1];
                
                // Parse and validate rating value
                int rating = parseRating(ratingStr);
                if (rating < 0) {
                    log.warn("Invalid rating value '{}' submitted for ticket {}", ratingStr, ticketIdStr);
                    return;
                }
                
                TicketId ticketId = new TicketId(Long.parseLong(ticketIdStr));
                log.info("Submitted rating {} for ticket {}", rating, ticketId);
                
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
                Rating ratingRecord = Rating.builder()
                    .rating(rating)
                    .submittedTs(String.valueOf(Instant.now().getEpochSecond()))
                    .status(TicketStatus.closed.name())
                    .impact(impact)
                    .tags(tags)
                    .isEscalated(isEscalated)
                    .build();
                
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

                Message queryMessage = slackClient.getMessageByTs(new SlackGetMessageByTsRequest(payload.getChannel().getId(), ticket.queryTs()));
                String userId = queryMessage.getUser();
                
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
                log.warn("Invalid rating action format after prefix removal: {}", remainder);
            }
        } catch (Exception e) {
            log.error("Error handling rating submission", e);
            throw e;
        }
    }

    private int parseRating(String rating) {
        try {
            int ratingValue = Integer.parseInt(rating);
            if (ratingValue >= 1 && ratingValue <= 5) {
                return ratingValue;
            } else {
                log.warn("Rating value {} is out of valid range (1-5)", ratingValue);
                return -1; // Return -1 to indicate invalid rating
            }
        } catch (NumberFormatException e) {
            log.warn("Rating value '{}' is not a valid integer", rating);
            return -1; // Return -1 to indicate invalid rating
        }
    }
}