package com.coreeng.supportbot.feedback.handler;

import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.SlackBlockActionHandler;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackPostEphemeralMessageRequest;
import com.coreeng.supportbot.slack.client.SimpleSlackMessage;
import com.slack.api.app_backend.interactive_components.payload.BlockActionPayload;
import com.slack.api.bolt.context.builtin.ActionContext;
import com.slack.api.bolt.request.builtin.BlockActionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
@Slf4j
@RequiredArgsConstructor
public class RatingHandler implements SlackBlockActionHandler {
    
    private final SlackClient slackClient;

    @Override
    public Pattern getPattern() {
        return Pattern.compile("rating_\\d+_.*");
    }

    @Override
    public void apply(BlockActionRequest req, ActionContext context) {
        BlockActionPayload payload = req.getPayload();
        
        for (BlockActionPayload.Action action : payload.getActions()) {
            String actionId = action.getActionId();
            
            if (actionId.startsWith("rating_")) {
                handleRating(payload, action, context);
            } else {
                log.warn("Unknown rating action: {}", actionId);
            }
        }
    }

    private void handleRating(BlockActionPayload payload, BlockActionPayload.Action action, ActionContext context) {
        String actionId = action.getActionId();
        
        // Extract rating, ticket ID, and thread timestamp from action ID: rating_5_TicketId[id=123]_1234567890.123456
        String[] parts = actionId.split("_");
        int rating = Integer.parseInt(parts[1]);
        String ticketId = parts[2];
        
        // Handle the case where ticket ID might contain brackets and thread timestamp is after the last underscore
        String threadTs = null;
        if (parts.length >= 4) {
            // If there are 4+ parts, the thread timestamp is likely the last part
            threadTs = parts[parts.length - 1];
        }
        
        log.info("User {} rated ticket {} with {} stars in thread {}", 
                 context.getRequestUserId(), ticketId, rating, threadTs);
        
        // Create thank you message
        String stars = "⭐".repeat(rating);
        String thankYouMessage = getThankYouMessage(rating);
        String fullMessage = "✅ " + thankYouMessage + " " + stars + "\n\nYour feedback has been recorded anonymously.";
        
        // Send new ephemeral acknowledgment message in the same thread
        slackClient.postEphemeralMessage(SlackPostEphemeralMessageRequest.builder()
            .message(SimpleSlackMessage.builder()
                .text(fullMessage)
                .build())
            .channel(payload.getChannel().getId())
            .userId(context.getRequestUserId())
            .threadTs(MessageTs.ofOrNull(threadTs))  // Use the extracted thread timestamp
            .build()
        );
            
        log.info("Acknowledged {} star rating for ticket {} in thread {}", rating, ticketId, threadTs);
        
        // TODO: Store the feedback in database
        // feedbackService.storeFeedback(ticketId, rating);
    }
    
    private String getThankYouMessage(int rating) {
        return switch (rating) {
            case 5 -> "Excellent! Thanks for the 5-star rating!";
            case 4 -> "Great! Thanks for the positive feedback!";
            case 3 -> "Good! Thanks for your honest feedback!";
            case 2 -> "Thanks! We'll work on improving!";
            case 1 -> "Thank you for the honest feedback. We'll do better!";
            default -> "Thank you for your feedback!";
        };
    }
}