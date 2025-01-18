package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.config.SlackTicketsProps;
import com.coreeng.supportbot.enums.ImpactsRegistry;
import com.coreeng.supportbot.enums.SlackTeamsRegistry;
import com.coreeng.supportbot.enums.Tag;
import com.coreeng.supportbot.enums.TagsRegistry;
import com.coreeng.supportbot.enums.TicketImpact;
import com.coreeng.supportbot.escalation.Escalation;
import com.coreeng.supportbot.escalation.EscalationId;
import com.coreeng.supportbot.escalation.EscalationQueryService;
import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.slack.client.SlackGetMessageByTsRequest;
import com.coreeng.supportbot.slack.client.SlackPostMessageRequest;
import com.coreeng.supportbot.slack.events.MessagePosted;
import com.coreeng.supportbot.slack.events.ReactionAdded;
import com.coreeng.supportbot.slack.events.SlackEvent;
import com.coreeng.supportbot.ticket.slack.TicketSlackService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.slack.api.model.Message;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Objects;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Comparator.comparing;

@Service
@Slf4j
@RequiredArgsConstructor
public class TicketProcessingService {
    private final TicketRepository repository;
    private final SlackClient slackClient;
    private final TicketSlackService slackService;
    private final SlackTicketsProps slackTicketsProps;
    private final EscalationQueryService escalationQueryService;
    private final SlackTeamsRegistry slackTeamsRegistry;
    private final ImpactsRegistry impactsRegistry;
    private final TagsRegistry tagsRegistry;
    private final ApplicationEventPublisher publisher;


    public void handleMessagePosted(MessagePosted e) {
        if (!isQueryEvent(e)) {
            return;
        }
        if (repository.createQueryIfNotExists(e.messageRef().actualThreadTs())) {
            log.atInfo()
                .addArgument(() -> e.messageRef().actualThreadTs())
                .log("Query is created on message({})");
        }
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

        Ticket newTicket = repository.createTicketIfNotExists(Ticket.createNew(e.messageRef().actualThreadTs(), e.messageRef().channelId()));
        log.atInfo()
            .addArgument(() -> e.messageRef().actualThreadTs())
            .log("Ticket is created on reaction to message({})");

        slackService.markPostTracked(new MessageRef(e.messageRef().actualThreadTs(), e.messageRef().channelId()));

        if (newTicket.createdMessageTs() == null) {
            // It's not really idempotent, since it's not atomic (one thing can succeed while the other one fail)
            // In the worst case, multiple forms will be posted, which is not a big issue
            MessageRef postedMessageRef = slackService.postTicketForm(
                new MessageRef(e.messageRef().actualThreadTs(), e.messageRef().channelId()),
                new TicketCreatedMessage(
                    newTicket.id(),
                    newTicket.status(),
                    newTicket.statusHistory().getLast().timestamp()
                )
            );
            repository.updateTicket(
                newTicket.toBuilder()
                    .createdMessageTs(postedMessageRef.ts())
                    .build()
            );
        } else {
            log.atInfo()
                .addArgument(() -> e.messageRef().actualThreadTs())
                .log("Ticket form is already posted to message({})");
        }
    }

    public ToggleResult toggleStatus(ToggleTicketAction action) {
        Ticket ticket = repository.findTicketByQuery(action.threadTs());
        if (ticket == null) {
            throw new IllegalArgumentException("Ticket not found");
        }

        TicketStatus nextStatus = TicketStatus.opened == ticket.status()
            ? TicketStatus.closed
            : TicketStatus.opened;

        if (nextStatus == TicketStatus.closed) {
            long unresolvedEscalations = escalationQueryService.countNotResolvedByTicketId(ticket.id());
            if (unresolvedEscalations > 0) {
                return new ToggleResult.RequiresConfirmation(
                    ticket.id(),
                    unresolvedEscalations
                );
            }
        }

        Ticket updatedTicket = repository.updateTicket(
            ticket.toBuilder()
                .status(nextStatus)
                .build()
        );
        log.atInfo()
            .addArgument(updatedTicket::id)
            .addArgument(updatedTicket::status)
            .log("Toggle ticket({}) status to {}");

        onStatusUpdate(updatedTicket);
        return new ToggleResult.Success();
    }

    public void close(TicketId ticketId) {
        Ticket ticket = repository.findTicketById(ticketId);
        if (ticket == null) {
            throw new IllegalArgumentException("Ticket not found");
        }
        if (ticket.status() == TicketStatus.closed) {
            log.warn("Ticket {} is already closed", ticketId);
            return;
        }
        ticket = repository.updateTicket(ticket.toBuilder()
            .status(TicketStatus.closed)
            .build());
        log.info("Ticket {} is closed", ticketId);

        onStatusUpdate(ticket);
    }

    public TicketSummaryView summaryView(TicketId id) {
        Ticket ticket = repository.findTicketById(id);
        if (ticket == null) {
            throw new IllegalStateException("Ticket not found");
        }
        SlackGetMessageByTsRequest messageRequest = new SlackGetMessageByTsRequest(
            ticket.channelId(),
            ticket.queryTs()
        );
        Message queryMessage = slackClient.getMessageByTs(messageRequest);
        String permalink = slackClient.getPermalink(messageRequest);
        TicketSummaryView.QuerySummaryView querySummary = new TicketSummaryView.QuerySummaryView(
            ImmutableList.copyOf(queryMessage.getBlocks()),
            new MessageTs(queryMessage.getTs()),
            queryMessage.getUser(),
            permalink
        );
        ImmutableList<TicketSummaryView.EscalationView> escalations = escalationQueryService
            .listByTicketId(ticket.id()).stream()
            .sorted(comparing(Escalation::openedAt))
            .map(e -> {
                String threadPermalink = slackClient.getPermalink(new SlackGetMessageByTsRequest(
                    e.channelId(), e.threadTs()
                ));
                return TicketSummaryView.EscalationView.of(e, threadPermalink);
            })
            .collect(toImmutableList());
        return TicketSummaryView.of(
            ticket,
            querySummary,
            escalations,
            tagsRegistry.listAllTags(),
            impactsRegistry.listAllImpacts()
        );
    }

    public Ticket submit(TicketSubmission submission) {
        Ticket ticket = repository.findTicketById(submission.ticketId());
        if (ticket == null) {
            throw new IllegalStateException("Ticket not found");
        }

        ImmutableList<Tag> newTags = tagsRegistry.listTagsByCodes(ImmutableSet.copyOf(submission.tags()));
        TicketImpact newImpact = impactsRegistry.findImpactByCode(submission.impact());

        Ticket updatedTicket = repository.updateTicket(
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

    public void escalate(EscalateRequest request) {
        Ticket ticket = repository.findTicketById(request.ticketId());
        if (ticket == null) {
            log.atWarn()
                .addArgument(request::teamId)
                .log("Trying to escalate non-existing ticket: {}");
            throw new IllegalArgumentException("Ticket doesn't exist");
        }
        if (ticket.status() == TicketStatus.closed) {
            log.atWarn()
                .addArgument(request::ticketId)
                .log("Trying to escalate closed ticket: {}");
            throw new IllegalArgumentException("Ticket is closed");
        }

        publisher.publishEvent(new TicketEscalated(
            ticket,
            request.teamId(),
            request.threadPermalink(),
            request.tags()
        ));
    }

    public void postTicketEscalatedMessage(EscalationId escalationId) {
        Escalation escalation = checkNotNull(
            escalationQueryService.findById(escalationId),
            "Escalation not found: {}", escalationId
        );
        Ticket ticket = checkNotNull(
            repository.findTicketById(escalation.ticketId()),
            "Ticket not found: {}", escalation.ticketId()
        );
        String escalationThreadPermalink = slackClient.getPermalink(new SlackGetMessageByTsRequest(
            escalation.channelId(), escalation.threadTs()
        ));
        slackClient.postMessage(new SlackPostMessageRequest(
            new TicketEscalatedMessage(
                escalationThreadPermalink,
                checkNotNull(slackTeamsRegistry.findSlackTeamById(escalation.teamId())).name()
            ),
            ticket.channelId(),
            ticket.queryTs()
        ));
    }

    @NotNull
    private Ticket onStatusUpdate(Ticket ticket) {
        publisher.publishEvent(new TicketStatusChanged(
            ticket.id(),
            ticket.status()
        ));
        Ticket updatedTicket = repository.insertStatusLog(ticket);
        slackService.editTicketForm(
            new MessageRef(updatedTicket.createdMessageTs(), updatedTicket.channelId()),
            new TicketCreatedMessage(
                updatedTicket.id(),
                updatedTicket.status(),
                updatedTicket.statusHistory().getLast().timestamp()
            )
        );
        if (updatedTicket.status() == TicketStatus.closed) {
            slackService.markTicketClosed(new MessageRef(
                updatedTicket.queryTs(),
                updatedTicket.channelId()
            ));
        } else {
            slackService.unmarkTicketClosed(new MessageRef(
                updatedTicket.queryTs(),
                updatedTicket.channelId()
            ));
        }
        return updatedTicket;
    }


    private boolean isQueryEvent(SlackEvent event) {
        return Objects.equals(slackTicketsProps.channelId(), event.messageRef().channelId())
            && !event.messageRef().isReply();
    }
}
