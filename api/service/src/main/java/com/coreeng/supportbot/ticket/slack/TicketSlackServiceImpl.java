package com.coreeng.supportbot.ticket.slack;

import com.coreeng.supportbot.config.SlackTicketsProps;
import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.SlackException;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackEditMessageRequest;
import com.coreeng.supportbot.slack.client.SlackGetMessageByTsRequest;
import com.coreeng.supportbot.slack.client.SlackPostEphemeralMessageRequest;
import com.coreeng.supportbot.slack.client.SlackPostMessageRequest;
import com.coreeng.supportbot.teams.SupportTeamService;
import com.coreeng.supportbot.ticket.TicketCreatedMessage;
import com.coreeng.supportbot.ticket.TicketCreatedMessageMapper;
import com.coreeng.supportbot.ticket.TicketEscalatedMessage;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.rating.RatingService;
import com.coreeng.supportbot.rating.RatingRequestMessage;
import com.coreeng.supportbot.ticket.TicketWentStaleMessage;
import com.slack.api.methods.request.reactions.ReactionsAddRequest;
import com.slack.api.methods.request.reactions.ReactionsRemoveRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.model.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketSlackServiceImpl implements TicketSlackService {
    private final SlackClient slackClient;
    private final SlackTicketsProps slackTicketsProps;
    private final SupportTeamService supportTeamService;
    private final TicketCreatedMessageMapper createdMessageMapper;
    private final RatingService ratingService;

    @Override
    public void markPostTracked(MessageRef threadRef) {
        addReactionToPostIfPresent(
            slackTicketsProps.responseInitialReaction(),
            threadRef
        );
    }

    @Override
    public void markTicketClosed(MessageRef threadRef) {
        addReactionToPostIfPresent(
            slackTicketsProps.resolvedReaction(),
            threadRef
        );
    }

    @Override
    public void markTicketEscalated(MessageRef threadRef) {
        addReactionToPostIfPresent(
                slackTicketsProps.escalatedReaction(),
                threadRef
        );
    }

    @Override
    public void unmarkTicketClosed(MessageRef threadRef) {
        removeReactionFromPostIfPresent(
            "white_check_mark",
            threadRef
        );
    }

    @Override
    public MessageRef postTicketForm(MessageRef threadRef, TicketCreatedMessage message) {
        ChatPostMessageResponse postMessageResponse = slackClient.postMessage(new SlackPostMessageRequest(
            createdMessageMapper.renderMessage(message),
            threadRef.channelId(),
            threadRef.ts()
        ));
        MessageTs ts = MessageTs.of(postMessageResponse.getTs());
        log.info("Ticket form is posted: {}", ts);
        return new MessageRef(
            ts,
            threadRef.ts(),
            postMessageResponse.getChannel()
        );
    }

    @Override
    public void editTicketForm(MessageRef threadRef, TicketCreatedMessage message) {
        if (threadRef.ts().mocked()) {
            log.atInfo()
                .addArgument(threadRef::ts)
                .log("Pretending to edit ticket form, because it's mocked: {}");
            return;
        }

        slackClient.editMessage(new SlackEditMessageRequest(
            createdMessageMapper.renderMessage(message),
            threadRef.channelId(),
            threadRef.ts()
        ));
        log.atInfo()
            .addArgument(threadRef::ts)
            .log("Ticket form is updated: {}");
    }

    @Override
    public void postTicketEscalatedMessage(MessageRef queryRef, MessageRef escalationThreadRef, String slackTeamName) {
        String escalationThreadPermalink = slackClient.getPermalink(SlackGetMessageByTsRequest.of(escalationThreadRef));
        slackClient.postMessage(new SlackPostMessageRequest(
            new TicketEscalatedMessage(
                escalationThreadPermalink,
                slackTeamName
            ),
            queryRef.channelId(),
            queryRef.ts()
        ));
    }

    @Override
    public void warnStaleness(MessageRef queryRef) {
        if (queryRef.ts().mocked()) {
            log.atInfo()
                .addArgument(queryRef::ts)
                .log("Pretending to mark ticket as stale, because it's mocked: {}");
            return;
        }

        Message queryMessage = slackClient.getMessageByTs(SlackGetMessageByTsRequest.of(queryRef));
        slackClient.postMessage(new SlackPostMessageRequest(
            new TicketWentStaleMessage(
                queryMessage.getUser(),
                supportTeamService.getSlackGroupId()
            ),
            queryRef.channelId(),
            queryRef.ts()
        ));
    }

    @Override
    public void postRatingRequest(MessageRef queryRef, TicketId ticketId, String userId) {
        if (queryRef.ts().mocked()) {
            log.atInfo()
                .addArgument(ticketId)
                .addArgument(queryRef::ts)
                .log("Pretending to post rating request for ticket({}), because it's mocked: {}");
            return;
        }

        log.info("Posting ephemeral rating request for ticket {} to user {}", ticketId, userId);

        // If the user voted before on the same thread, we don't want them to do so again
        String anonymousId = ratingService.createAnonymousId(String.valueOf(ticketId.id()), userId);

        if (ratingService.hasAlreadyRated(anonymousId)) {
            log.info("User {} already submitted a rating for ticket {} - ignoring duplicate", userId, ticketId.render());
            return;
        }

        RatingRequestMessage ratingMessage = new RatingRequestMessage(ticketId);

        slackClient.postEphemeralMessage(SlackPostEphemeralMessageRequest.builder()
            .message(ratingMessage)
            .channel(queryRef.channelId())
            .threadTs(queryRef.ts())
            .userId(userId)
            .build()
        );

        log.info("Ephemeral rating request posted for ticket {} to user {}", ticketId, userId);
    }

    private void addReactionToPostIfPresent(
        String name,
        MessageRef messageRef
    ) {
        if (messageRef.ts().mocked()) {
            log.atInfo()
                .addArgument(name)
                .addArgument(messageRef::ts)
                .log("Pretending to add reaction({}) to message, because it's mocked: {}");
            return;
        }
        try {
            slackClient.addReaction(ReactionsAddRequest.builder()
                .name(name)
                .channel(messageRef.channelId())
                .timestamp(messageRef.ts().ts())
                .build());

            log.atInfo()
                .addArgument(name)
                .addArgument(messageRef.ts())
                .log("Reaction({}) is posted to message({})");
        } catch (SlackException exc) {
            if (Objects.equals("already_reacted", exc.getError())) {
                log.atInfo()
                    .addArgument(messageRef.ts())
                    .log("Reaction is already posted by bot to message({})");
            } else {
                throw exc;
            }
        }

    }

    private void removeReactionFromPostIfPresent(
        String name,
        MessageRef messageRef
    ) {
        if (messageRef.ts().mocked()) {
            log.atInfo()
                .addArgument(name)
                .addArgument(messageRef::ts)
                .log("Pretending to remove reaction({}) from message, because it's mocked: {}");
            return;
        }
        try {
            slackClient.removeReaction(ReactionsRemoveRequest.builder()
                .name(name)
                .timestamp(messageRef.ts().ts())
                .channel(messageRef.channelId())
                .build());

            log.atInfo()
                .addArgument(name)
                .addArgument(messageRef.ts())
                .log("Reaction({}) is removed from message({})");
        } catch (SlackException e) {
            if (Objects.equals("no_reaction", e.getError())) {
                log.atInfo()
                    .addArgument(name)
                    .addArgument(messageRef.ts())
                    .log("Reaction({}) is already absent from message({})");
            } else {
                throw e;
            }
        }
    }
}
