package com.coreeng.supportbot.ticket.slack;

import com.coreeng.supportbot.config.SlackTicketsProps;
import com.coreeng.supportbot.rating.RatingRequestMessage;
import com.coreeng.supportbot.rating.RatingRequestMessageMapper;
import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.SlackException;
import com.coreeng.supportbot.slack.SlackId;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackEditMessageRequest;
import com.coreeng.supportbot.slack.client.SlackGetMessageByTsRequest;
import com.coreeng.supportbot.slack.client.SlackMessage;
import com.coreeng.supportbot.slack.client.SlackPostEphemeralMessageRequest;
import com.coreeng.supportbot.slack.client.SlackPostMessageRequest;
import com.coreeng.supportbot.ticket.TicketCreatedMessage;
import com.coreeng.supportbot.ticket.TicketCreatedMessageMapper;
import com.coreeng.supportbot.ticket.TicketWentStaleMessage;
import com.coreeng.supportbot.ticket.TicketId;
import com.slack.api.methods.request.conversations.ConversationsRepliesRequest;
import com.slack.api.methods.request.reactions.ReactionsAddRequest;
import com.slack.api.methods.request.reactions.ReactionsRemoveRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.methods.response.conversations.ConversationsRepliesResponse;
import com.slack.api.model.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

import static org.springframework.util.CollectionUtils.isEmpty;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketSlackServiceImpl implements TicketSlackService {
    private final SlackClient slackClient;
    private final SlackTicketsProps slackTicketsProps;
    private final TicketCreatedMessageMapper createdMessageMapper;
    private final RatingRequestMessageMapper ratingReqMessageMapper;

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
    public void warnStaleness(MessageRef queryRef) {
        if (queryRef.ts().mocked()) {
            log.atInfo()
                .addArgument(queryRef::ts)
                .log("Pretending to mark ticket as stale, because it's mocked: {}");
            return;
        }

        Message queryMessage = slackClient.getMessageByTs(SlackGetMessageByTsRequest.of(queryRef));
        slackClient.postMessage(new SlackPostMessageRequest(
            new TicketWentStaleMessage(queryMessage.getUser()),
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

        if (userId == null) {
            if (log.isWarnEnabled()) {
                log.warn("Could not determine user for ticket {} rating request", ticketId);
            }
            return;
        }

        if (SlackId.slackbot.id().equals(userId)) {
            log.info("Skipping rating request for ticket {} because author is Slackbot (userId={})", ticketId, userId);
            return;
        }

        log.info("Posting ephemeral rating request for ticket {} to user {}", ticketId, userId);
        SlackMessage message = ratingReqMessageMapper.renderRatingRequestMessage(new RatingRequestMessage(ticketId));
        slackClient.postEphemeralMessage(SlackPostEphemeralMessageRequest.builder()
            .message(message)
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

    @Override
    public boolean isThreadReply(MessageRef messageRef) {
        if (messageRef.ts().mocked()) {
            return messageRef.isReply();
        }

        try {
            ConversationsRepliesResponse response = slackClient.getThreadPage(
                ConversationsRepliesRequest.builder()
                    .channel(messageRef.channelId())
                    .ts(messageRef.ts().ts())
                    // despite limit 1, Slack will send 2 messages if it's a thread reply
                    // first one is the original message, the second one is the reply
                    .limit(1)
                    .inclusive(true)
                    .build()
            );

            List<Message> messages = response.getMessages();
            if (isEmpty(messages)) {
                log.atDebug()
                    .addArgument(messageRef::ts)
                    .log("No messages found for ts({}). Assuming it's not a thread reply.");
                return false;
            }

            return messages.stream()
                .filter(m -> m.getTs() != null && m.getTs().equals(messageRef.ts().ts()))
                .findAny()
                .map(m -> {
                    // Slack doesn't set thread_ts in case it's a single message in the thread,
                    // But in case there are multiple messages, it sets it for all of them,
                    // So we can assume it's a thread reply if thread_ts == ts
                    boolean isReply = m.getThreadTs() != null && !m.getThreadTs().equals(m.getTs());
                    if (isReply) {
                        log.atDebug()
                            .addKeyValue("ts", m.getTs())
                            .log("Message is a thread reply");
                    } else {
                        log.atDebug()
                            .addKeyValue("ts", m.getTs())
                            .log("Message is not a thread reply");
                    }
                    return isReply;
                })
                .orElse(false);
        } catch (Exception ex) {
            log.atWarn()
                .addArgument(messageRef::ts)
                .setCause(ex)
                .log("Failed to fetch message({}) to check if it's a thread reply. Assuming it's not.");
            return false;
        }
    }
}
