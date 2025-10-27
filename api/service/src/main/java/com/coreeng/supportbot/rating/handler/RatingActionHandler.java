package com.coreeng.supportbot.rating.handler;

import com.coreeng.supportbot.rating.Rating;
import com.coreeng.supportbot.rating.RatingButtonInput;
import com.coreeng.supportbot.rating.RatingRequestMessageMapper;
import com.coreeng.supportbot.rating.RatingService;
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
import com.slack.api.model.Message;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class RatingActionHandler implements SlackBlockActionHandler {
    public static final String actionId = "rating-submit";


    private final RatingRequestMessageMapper mapper;
    private final RatingService service;
    private final SlackClient slackClient;

    public static String actionId(int rating) {
        return actionId + "-" + rating;
    }

    @Override
    public Pattern getPattern() {
        return Pattern.compile("^" + actionId + "-\\d" + "$");
    }

    @Override
    public void apply(BlockActionRequest req, ActionContext context) throws IOException, SlackApiException {
        BlockActionPayload payload = req.getPayload();

        for (BlockActionPayload.Action action : payload.getActions()) {
            handleRatingSubmission(action, payload);
        }
    }

    private void handleRatingSubmission(BlockActionPayload.Action action, BlockActionPayload payload) {
        RatingButtonInput input = mapper.parseButtonInput(action.getValue());
        Rating rating = service.save(input.ticketId(), input.rating());
        if (rating != null) {
            sendConfirmationReply(rating, MessageRef.from(payload));
        }
    }

    private void sendConfirmationReply(Rating rating, MessageRef threadRef) {
        Message queryMessage = slackClient.getMessageByTs(new SlackGetMessageByTsRequest(threadRef.channelId(), threadRef.threadTs()));
        String userId = queryMessage.getUser();

        slackClient.postEphemeralMessage(SlackPostEphemeralMessageRequest.builder()
            .message(SimpleSlackMessage.builder()
                .text(String.format("Thank you for your %d star rating! Your feedback has been recorded.", rating.rating()))
                .build())
            .channel(threadRef.channelId())
            .threadTs(threadRef.threadTs())
            .userId(userId)
            .build()
        );
    }
}