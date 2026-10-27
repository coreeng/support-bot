package com.coreeng.supportbot.rating.handler;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.slack.api.model.view.Views.view;

import com.coreeng.supportbot.rating.Rating;
import com.coreeng.supportbot.rating.RatingButtonInput;
import com.coreeng.supportbot.rating.RatingFeedbackViewInput;
import com.coreeng.supportbot.rating.RatingFeedbackViewMapper;
import com.coreeng.supportbot.rating.RatingRequestMessageMapper;
import com.coreeng.supportbot.rating.RatingService;
import com.coreeng.supportbot.rating.RatingTicketNotFoundException;
import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.SlackBlockActionHandler;
import com.coreeng.supportbot.slack.client.SimpleSlackMessage;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackGetMessageByTsRequest;
import com.coreeng.supportbot.slack.client.SlackPostEphemeralMessageRequest;
import com.slack.api.app_backend.interactive_components.payload.BlockActionPayload;
import com.slack.api.bolt.context.builtin.ActionContext;
import com.slack.api.bolt.request.builtin.BlockActionRequest;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.views.ViewsOpenRequest;
import com.slack.api.model.Message;
import java.io.IOException;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RatingActionHandler implements SlackBlockActionHandler {
    public static final String ACTION_ID = "rating-submit";

    private final RatingRequestMessageMapper mapper;
    private final RatingFeedbackViewMapper feedbackViewMapper;
    private final RatingService service;
    private final SlackClient slackClient;

    public static String actionId(int rating) {
        return ACTION_ID + "-" + rating;
    }

    @Override
    public Pattern getPattern() {
        return Pattern.compile("^" + ACTION_ID + "-\\d" + "$");
    }

    @Override
    public void apply(BlockActionRequest req, ActionContext context) throws IOException, SlackApiException {
        BlockActionPayload payload = req.getPayload();

        for (BlockActionPayload.Action action : payload.getActions()) {
            handleRatingSubmission(action, payload, context);
        }
    }

    private void handleRatingSubmission(
            BlockActionPayload.Action action, BlockActionPayload payload, ActionContext context) {
        RatingButtonInput input = mapper.parseButtonInput(action.getValue());
        try {
            Rating rating = service.save(input.ticketId(), input.rating());
            // Open the follow-up modal first: the trigger id expires a few seconds after the click
            openFeedbackModal(
                    context, new RatingFeedbackViewInput(checkNotNull(rating.id()), input.ticketId(), rating.rating()));
            sendConfirmationReply(rating, MessageRef.from(payload));
        } catch (IllegalArgumentException | RatingTicketNotFoundException e) {
            log.warn("Ignoring invalid rating submission for ticket {}: {}", input.ticketId(), e.getMessage());
        }
    }

    private void openFeedbackModal(ActionContext context, RatingFeedbackViewInput viewInput) {
        try {
            slackClient.viewsOpen(ViewsOpenRequest.builder()
                    .triggerId(context.getTriggerId())
                    .view(view(v -> feedbackViewMapper.render(viewInput, v)))
                    .build());
        } catch (RuntimeException e) {
            // The rating is already recorded; feedback is optional, so don't fail the whole interaction
            log.atWarn().setCause(e).log("Failed to open rating feedback modal");
        }
    }

    private void sendConfirmationReply(Rating rating, MessageRef threadRef) {
        Message queryMessage = slackClient.getMessageByTs(
                new SlackGetMessageByTsRequest(threadRef.channelId(), threadRef.actualThreadTs()));
        String userId = queryMessage.getUser();

        slackClient.postEphemeralMessage(SlackPostEphemeralMessageRequest.builder()
                .message(SimpleSlackMessage.builder()
                        .text(String.format(
                                "Thank you for your %d star rating! Your feedback has been recorded.", rating.rating()))
                        .build())
                .channel(threadRef.channelId())
                .threadTs(threadRef.actualThreadTs())
                .userId(userId)
                .build());
    }
}
