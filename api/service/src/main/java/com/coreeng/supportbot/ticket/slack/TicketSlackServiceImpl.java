package com.coreeng.supportbot.ticket.slack;

import com.coreeng.supportbot.config.SlackTicketsProps;
import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.SlackException;
import com.coreeng.supportbot.slack.client.SimpleSlackMessage;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackEditMessageRequest;
import com.coreeng.supportbot.slack.client.SlackGetMessageByTsRequest;
import com.coreeng.supportbot.slack.client.SlackPostEphemeralMessageRequest;
import com.coreeng.supportbot.slack.client.SlackPostMessageRequest;
import com.coreeng.supportbot.teams.SupportTeamService;
import com.coreeng.supportbot.ticket.TicketCreatedMessage;
import com.coreeng.supportbot.ticket.TicketCreatedMessageMapper;
import com.coreeng.supportbot.ticket.TicketEscalatedMessage;
import com.coreeng.supportbot.ticket.TicketWentStaleMessage;
import com.google.common.collect.ImmutableList;
import com.slack.api.methods.request.reactions.ReactionsAddRequest;
import com.slack.api.methods.request.reactions.ReactionsRemoveRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.model.Message;
import com.slack.api.model.block.ActionsBlock;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.SectionBlock;
import com.slack.api.model.block.composition.PlainTextObject;
import com.slack.api.model.block.element.ButtonElement;
import static com.slack.api.model.block.Blocks.*;

import java.util.List;
import java.util.Arrays;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;

import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.composition.BlockCompositions.plainText;
import static com.slack.api.model.block.element.BlockElements.button;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketSlackServiceImpl implements TicketSlackService {
    private final SlackClient slackClient;
    private final SlackTicketsProps slackTicketsProps;
    private final SupportTeamService supportTeamService;
    private final TicketCreatedMessageMapper createdMessageMapper;

   // Add this method to TicketSlackServiceImpl class
    @Override
    public void requestFeedback(MessageRef threadRef, String ticketId, String userId) {

        log.info("Requesting ratings for ticket: {} from user: {} in channel: {} with threadTs: {}", ticketId, userId,threadRef.channelId(), threadRef.ts());
        if (threadRef.ts().mocked()) {
            log.atInfo()
                .addArgument(ticketId)
                .log("Pretending to request feedback for ticket, because it's mocked: {}");
            return;
        }

        log.info("Requesting feedback for ticket: {} from user: {}", ticketId, userId);
        
        SimpleSlackMessage message = SimpleSlackMessage.builder()
            .text("Rate your support experience")
            .blocks(ImmutableList.of(
                section(s -> s
                    .text(plainText("How was your support experience?\nYour feedback helps us improve."))
                ),
                actions(ImmutableList.of(
                    button(b -> b
                        .actionId("rating_1_" + ticketId + "_" + threadRef.ts().ts())
                        .text(plainText("⭐ (1)"))
                        .value("1")
                    ),
                    button(b -> b
                        .actionId("rating_2_" + ticketId + "_" + threadRef.ts().ts())
                        .text(plainText("⭐⭐ (2)"))
                        .value("2")
                    ),
                    button(b -> b
                        .actionId("rating_3_" + ticketId + "_" + threadRef.ts().ts())
                        .text(plainText("⭐⭐⭐ (3)"))
                        .value("3")
                    ),
                    button(b -> b
                        .actionId("rating_4_" + ticketId + "_" + threadRef.ts().ts())
                        .text(plainText("⭐⭐⭐⭐ (4)"))
                        .value("4")
                    ),
                    button(b -> b
                        .actionId("rating_5_" + ticketId + "_" + threadRef.ts().ts())
                        .text(plainText("⭐⭐⭐⭐⭐ (5)"))
                        .value("5")
                    )
                ))
            ))
            .build();
        
        slackClient.postEphemeralMessage(SlackPostEphemeralMessageRequest.builder()
            .message(message)
            .channel(threadRef.channelId())
            .threadTs(threadRef.ts())
            .userId(userId)
            .build()
        );
    }
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

        Message queryMessage = slackClient.getMessageByTs(SlackGetMessageByTsRequest.of(threadRef));
        requestFeedback(threadRef, message.ticketId().toString(), queryMessage.getUser());

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
