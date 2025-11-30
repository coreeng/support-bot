package com.coreeng.supportbot.ticket;

import com.coreeng.supportbot.config.LogEnricher;
import com.coreeng.supportbot.config.SlackTicketsProps;
import com.coreeng.supportbot.escalation.EscalationQueryService;
import com.coreeng.supportbot.slack.MessageRef;
import com.coreeng.supportbot.slack.events.MessagePosted;
import com.coreeng.supportbot.slack.events.ReactionAdded;
import com.coreeng.supportbot.slack.events.SlackEvent;
import com.coreeng.supportbot.ticket.slack.TicketSlackService;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class TicketProcessingService {
    private final TicketRepository repository;
    private final TicketSlackService slackService;
    private final EscalationQueryService escalationQueryService;
    private final SlackTicketsProps slackTicketsProps;
    private final ApplicationEventPublisher publisher;
    private final LogEnricher logEnricher;

    public void handleMessagePosted(MessagePosted e) {
        if (isQueryEvent(e)) {
            repository.createQueryIfNotExists(e.messageRef());
            log.atInfo()
                .addArgument(e::messageRef)
                .log("Query is created on message({})");
            return;
        }

        MessageRef queryRef = e.messageRef().toThreadRef();
        Ticket ticket = repository.findTicketByQuery(queryRef);
        if (ticket == null) {
            log.atDebug()
                .addArgument(queryRef::ts)
                .log("No ticket for query({}). Ignoring posted message.");
            return;
        }

        if (ticket.status() == TicketStatus.stale) {
            Ticket updatedTicket = repository.updateTicket(
                ticket.toBuilder()
                    .status(TicketStatus.opened)
                    .lastInteractedAt(Instant.now())
                    .build()
            );
            onStatusUpdate(updatedTicket);
        } else {
            repository.touchTicketById(ticket.id(), Instant.now());
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
            .addArgument(() -> logEnricher.kv("action", "ticket_created"))
            .addArgument(() -> logEnricher.kv("id", newTicket.id().id()))
            .addArgument(() -> logEnricher.kv("status", newTicket.status()))
            .addArgument(() -> logEnricher.kv("channel_id", e.messageRef().channelId()))
            .log("Ticket created on reaction to message({}) {} {} {} {}");

        slackService.markPostTracked(new MessageRef(e.messageRef().actualThreadTs(), e.messageRef().channelId()));

        if (newTicket.createdMessageTs() == null) {
            // It's not really idempotent, since it's not atomic (one thing can succeed while the other one fail)
            // In the worst case, multiple forms will be posted, which is not a big issue
            MessageRef postedMessageRef = slackService.postTicketForm(
                new MessageRef(e.messageRef().actualThreadTs(), e.messageRef().channelId()),
                new TicketCreatedMessage(
                    newTicket.id(),
                    newTicket.status(),
                    newTicket.statusLog().getLast().date()
                )
            );
            repository.updateTicket(
                newTicket.toBuilder()
                    .createdMessageTs(postedMessageRef.ts())
                    .lastInteractedAt(Instant.now())
                    .build()
            );
        } else {
            log.atInfo()
                .addArgument(() -> e.messageRef().actualThreadTs())
                .log("Ticket form is already posted to message({})");
        }
    }

    public TicketSubmitResult submit(TicketSubmission submission) {
        Ticket ticket = repository.findTicketById(submission.ticketId());
        if (ticket == null) {
            throw new IllegalStateException("Ticket not found: " + submission.ticketId());
        }

        if (!submission.confirmed()
            && ticket.status() != TicketStatus.closed
            && submission.status() == TicketStatus.closed) {
            long unresolvedEscalations = escalationQueryService.countNotResolvedByTicketId(ticket.id());
            if (unresolvedEscalations > 0) {
                return new TicketSubmitResult.RequiresConfirmation(
                    submission,
                    new TicketSubmitResult.ConfirmationCause(unresolvedEscalations)
                );
            }
        }

        Ticket updatedTicket = repository.updateTicket(
            ticket.toBuilder()
                .status(submission.status())
                .team(submission.authorsTeam())
                .tags(submission.tags())
                .impact(submission.impact())
                .lastInteractedAt(Instant.now())
                .build()
        );

        log.atInfo()
            .addArgument(() -> logEnricher.kv("action", "ticket_updated"))
            .addArgument(() -> logEnricher.kv("id", updatedTicket.id().id()))
            .addArgument(() -> logEnricher.kv("status", updatedTicket.status()))
            .addArgument(() -> logEnricher.kv("team", updatedTicket.team()))
            .addArgument(() -> logEnricher.kv("impact", updatedTicket.impact()))
            .addArgument(() -> logEnricher.kv("tags", updatedTicket.tags()))
            .log("Ticket submitted {} {} {} {} {} {}");

        if (ticket.status() != updatedTicket.status()) {
            onStatusUpdate(updatedTicket);
        }
        return new TicketSubmitResult.Success();
    }

    public void escalate(EscalateRequest request) {
        Ticket ticket = repository.findTicketById(request.ticketId());
        if (ticket == null) {
            log.atWarn()
                .addArgument(request::ticketId)
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
            request.team(),
            request.threadPermalink(),
            request.tags()
        ));
        log.atInfo()
            .addArgument(() -> logEnricher.kv("action", "ticket_escalated"))
            .addArgument(() -> logEnricher.kv("id", ticket.id().id()))
            .addArgument(() -> logEnricher.kv("escalation_team", request.team()))
            .addArgument(() -> logEnricher.kv("team", ticket.team()))
            .addArgument(() -> logEnricher.kv("tags", request.tags()))
            .log("Ticket escalated {} {} {} {} {}");
        slackService.markTicketEscalated(ticket.queryRef());
    }

    public void markAsStale(TicketId ticketId) {
        Ticket ticket = repository.findTicketById(ticketId);
        if (ticket == null) {
            log.warn("Ticket with id {} not found", ticketId);
            return;
        }
        if (ticket.status() != TicketStatus.opened) {
            log.atWarn()
                .addArgument(ticket::id)
                .log("Ticket({}) can't be marked stale, because it's not open anymore.");
            return;
        }

        log.info("Marking ticket {} as stale", ticketId);
        slackService.warnStaleness(ticket.queryRef());
        Ticket updatedTicket = repository.updateTicket(
            ticket.toBuilder()
                .status(TicketStatus.stale)
                .lastInteractedAt(Instant.now())
                .build()
        );
        onStatusUpdate(updatedTicket);
    }

    public void remindOfStaleTicket(TicketId ticketId) {
        Ticket ticket = repository.findTicketById(ticketId);
        if (ticket == null) {
            log.warn("Ticket with id {} not found", ticketId);
            return;
        }
        if (ticket.status() != TicketStatus.stale) {
            log.atWarn()
                .addArgument(ticket::id)
                .log("Ticket({}) is not stale, skipping remind");
            return;
        }

        log.info("Reminding of stale ticket {}", ticketId);
        slackService.warnStaleness(ticket.queryRef());
        repository.touchTicketById(ticketId, Instant.now());
    }

    @NotNull
    private Ticket onStatusUpdate(Ticket ticket) {
        publisher.publishEvent(new TicketStatusChanged(
            ticket.id(),
            ticket.status()
        ));
        Ticket updatedTicket = repository.insertStatusLog(ticket, Instant.now());
        log.atInfo()
            .addArgument(() -> logEnricher.kv("action", "ticket_changed"))
            .addArgument(() -> logEnricher.kv("id", updatedTicket.id().id()))
            .addArgument(() -> logEnricher.kv("status", updatedTicket.status()))
            .addArgument(() -> logEnricher.kv("team", updatedTicket.team()))
            .addArgument(() -> logEnricher.kv("impact", updatedTicket.impact()))
            .addArgument(() -> logEnricher.kv("tags", updatedTicket.tags()))
            .log("Ticket status changed {} {} {} {} {} {}");
        slackService.editTicketForm(
            new MessageRef(updatedTicket.createdMessageTs(), updatedTicket.channelId()),
            new TicketCreatedMessage(
                updatedTicket.id(),
                updatedTicket.status(),
                updatedTicket.statusLog().getLast().date()
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

    /**
     * Check if a ticket can be rated (i.e., hasn't been rated yet)
     */
    public boolean canRateTicket(TicketId ticketId) {
        return !repository.isTicketRated(ticketId);
    }

    private boolean isQueryEvent(SlackEvent event) {
        return Objects.equals(slackTicketsProps.channelId(), event.messageRef().channelId())
            && !event.messageRef().isReply();
    }
}
