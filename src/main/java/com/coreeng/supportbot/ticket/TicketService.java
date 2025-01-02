package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.EnumerationValue;
import com.coreeng.supportbot.config.SlackTicketsProps;
import com.coreeng.supportbot.config.TicketProps;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.SlackException;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackEditMessageRequest;
import com.coreeng.supportbot.slack.client.SlackGetMessageByTsRequest;
import com.coreeng.supportbot.slack.client.SlackPostMessageRequest;
import com.coreeng.supportbot.slack.events.MessagePosted;
import com.coreeng.supportbot.slack.events.ReactionAdded;
import com.coreeng.supportbot.slack.events.SlackEvent;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.slack.api.methods.request.reactions.ReactionsAddRequest;
import com.slack.api.methods.request.reactions.ReactionsRemoveRequest;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.model.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Objects;

import static com.google.common.collect.ImmutableList.toImmutableList;

@Service
@Slf4j
@RequiredArgsConstructor
public class TicketService {
    private final TicketRepository ticketRepository;
    private final SlackClient slackClient;
    private final SlackTicketsProps slackTicketsProps;
    private final TicketProps ticketProps;
    private final DateTimeFormatter dateFormatter;


    public void handleMessagePosted(MessagePosted e) {
        if (!isQueryEvent(e)) {
            return;
        }
        ticketRepository.createQueryIfNotExists(e.messageTs());
        log.atInfo()
            .addArgument(e::messageTs)
            .log("Query is created on message({})");
    }

    /**
     * This method should be idempotent, so users can re-add reaction without any visible problem.
     * It can also be used as a way to try to fix any transient problem
     */
    public void handleReactionAdded(ReactionAdded e) {
        if (!isQueryEvent(e)) {
            return;
        }

        if (!Objects.equals(e.reaction(), slackTicketsProps.expectedInitialReaction())) {
            log.atDebug()
                .addArgument(e::reaction)
                .log("Skipping event. Unexpected reaction({})");
            return;
        }

        Ticket newTicket = ticketRepository.createTicketIfNotExists(Ticket.createNew(e.messageTs(), e.channelId()));
        log.atInfo()
            .addArgument(e::messageTs)
            .log("Ticket is created on reaction to message({})");

        addReactionToPostIfAbsent(
            slackTicketsProps.responseInitialReaction(),
            e.messageTs(),
            e.channelId()
        );

        if (newTicket.createdMessageTs() == null) {
            // It's not really idempotent, since it's not atomic (one thing can succeed while the other one fail)
            // In the worst case, multiple forms will be posted, which is not a big issue
            ChatPostMessageResponse postMessageResponse = slackClient.postMessage(new SlackPostMessageRequest(
                new TicketCreatedMessage(
                    newTicket.id(),
                    newTicket.status(),
                    newTicket.statusHistory().getLast().timestamp(),
                    dateFormatter
                ),
                e.channelId(),
                e.messageTs()
            ));
            newTicket = ticketRepository.updateTicket(
                newTicket.toBuilder()
                    .createdMessageTs(MessageTs.of(postMessageResponse.getTs()))
                    .build()
            );
            log.atInfo()
                .addArgument(newTicket::queryTs)
                .log("Ticket form is posted for query({})");
        } else {
            log.atInfo()
                .addArgument(e::messageTs)
                .log("Ticket form is already posted to message({})");
        }
    }

    public void toggleStatus(ToggleTicketAction action) {
        Ticket ticket = ticketRepository.findTicketByQuery(action.threadTs());
        if (ticket == null) {
            log.atWarn()
                .addArgument(action::threadTs)
                .log("Couldn't find ticket by queryTs({}) to toggle its status");
            return;
        }

        TicketStatus nextStatus = TicketStatus.unresolved == ticket.status()
            ? TicketStatus.resolved
            : TicketStatus.unresolved;
        Ticket updatedTicket = ticketRepository.updateTicket(
            ticket.toBuilder()
                .status(nextStatus)
                .build()
        );
        log.atInfo()
            .addArgument(updatedTicket::id)
            .addArgument(updatedTicket::status)
            .log("Toggle ticket({}) status to {}");

        onStatusUpdate(updatedTicket);
    }

    public TicketSummaryView summaryView(TicketSummaryViewQuery query) {
        Ticket ticket = ticketRepository.findTicketByQuery(query.queryTs());
        if (ticket == null) {
            throw new IllegalStateException("Ticket not found");
        }
        SlackGetMessageByTsRequest messageRequest = new SlackGetMessageByTsRequest(
            query.channelId(),
            query.queryTs()
        );
        Message queryMessage = slackClient.getMessageByTs(messageRequest);
        String permalink = slackClient.getPermalink(messageRequest);
        TicketSummaryView.QuerySummaryView querySummary = new TicketSummaryView.QuerySummaryView(
            ImmutableList.copyOf(queryMessage.getBlocks()),
            new MessageTs(queryMessage.getTs()).getDate(),
            queryMessage.getUser(),
            permalink
        );
        return TicketSummaryView.of(ticket, querySummary, ticketProps.tags(), ticketProps.impacts());
    }

    public Ticket submit(TicketSubmission submission) {
        Ticket ticket = ticketRepository.findTicketById(submission.ticketId());
        if (ticket == null) {
            throw new IllegalStateException("Ticket not found");
        }

        ImmutableSet<String> submittedTags = ImmutableSet.copyOf(submission.tags());
        ImmutableList<EnumerationValue> newTags = ticketProps.tags().stream()
            .filter(tag -> submittedTags.contains(tag.code()))
            .collect(toImmutableList());

        EnumerationValue newImpact = ticketProps.impacts().stream()
            .filter(impact -> Objects.equals(impact.code(), submission.impact()))
            .findAny()
            .orElse(null);

        Ticket updatedTicket = ticketRepository.updateTicket(
            ticket.toBuilder()
                .status(submission.status())
                .tags(newTags)
                .impact(newImpact)
                .build()
        );

        if (ticket.status() != updatedTicket.status()) {
            updatedTicket = onStatusUpdate(updatedTicket);
        }
        return updatedTicket;
    }

    @NotNull
    private Ticket onStatusUpdate(Ticket ticket) {
        Ticket updatedTicket = ticketRepository.insertStatusLog(ticket);
        slackClient.editMessage(new SlackEditMessageRequest(
            new TicketCreatedMessage(
                updatedTicket.id(),
                updatedTicket.status(),
                updatedTicket.statusHistory().getLast().timestamp(),
                dateFormatter
            ),
            updatedTicket.channelId(),
            updatedTicket.createdMessageTs()
        ));
        log.atInfo()
            .addArgument(updatedTicket::queryTs)
            .log("Ticket form is updated for query({})");
        if (updatedTicket.status() == TicketStatus.resolved) {
            addReactionToPostIfAbsent(
                "white_check_mark",
                updatedTicket.queryTs(),
                updatedTicket.channelId()
            );
        } else {
            removeReactionFromPostIfPresent(
                "white_check_mark",
                updatedTicket.queryTs(),
                updatedTicket.channelId()
            );
        }
        return updatedTicket;
    }

    private void addReactionToPostIfAbsent(
        String name,
        MessageTs messageTs,
        String channelId
    ) {
        try {
            slackClient.addReaction(ReactionsAddRequest.builder()
                .name(name)
                .channel(channelId)
                .timestamp(messageTs.ts())
                .build());

            log.atInfo()
                .addArgument(name)
                .addArgument(messageTs)
                .log("Reaction({}) is posted to message({})");
        } catch (SlackException exc) {
            if (Objects.equals("already_added", exc.getError())) {
                log.atInfo()
                    .addArgument(messageTs)
                    .log("Reaction is already posted by bot to message({})");
            } else {
                throw exc;
            }
        }
    }

    private void removeReactionFromPostIfPresent(
        String name,
        MessageTs messageTs,
        String channelId
    ) {
        try {
            slackClient.removeReaction(ReactionsRemoveRequest.builder()
                .name(name)
                .timestamp(messageTs.ts())
                .channel(channelId)
                .build());

            log.atInfo()
                .addArgument(name)
                .addArgument(messageTs)
                .log("Reaction({}) is removed from message({})");
        } catch (SlackException e) {
            if (Objects.equals("already_removed", e.getError())) {
                log.atInfo()
                    .addArgument(name)
                    .addArgument(messageTs)
                    .log("Reaction({}) is already absent from message({})");
            } else {
                throw e;
            }
        }
    }

    private boolean isQueryEvent(SlackEvent event) {
        return Objects.equals(slackTicketsProps.channelId(), event.channelId())
            && event.threadTs() == null;
    }
}
