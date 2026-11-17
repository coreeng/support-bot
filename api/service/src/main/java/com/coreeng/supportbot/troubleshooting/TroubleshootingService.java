package com.coreeng.supportbot.troubleshooting;

import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketQueryService;
import com.coreeng.supportbot.troubleshooting.Payloads.DecisionKind;
import com.coreeng.supportbot.troubleshooting.Payloads.EngineerMessage;
import com.coreeng.supportbot.troubleshooting.router.RouterAction;
import com.coreeng.supportbot.troubleshooting.router.RouterTurnRunner;
import com.google.common.collect.ImmutableList;
import java.util.Objects;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** What the REST layer may do with sessions. */
@Service
@ConditionalOnTroubleshootingEnabled
@Slf4j
public class TroubleshootingService {
    private final TroubleshootingSessionService sessions;
    private final TicketQueryService tickets;
    private final SlackThreadSnapshotter snapshotter;
    private final RouterTurnRunner turns;
    private final CannedRunService cannedRuns;
    private final TroubleshootingEventBroadcaster broadcaster;
    private final TroubleshootingExecutor executor;

    public TroubleshootingService(
            TroubleshootingSessionService sessions,
            TicketQueryService tickets,
            SlackThreadSnapshotter snapshotter,
            RouterTurnRunner turns,
            CannedRunService cannedRuns,
            TroubleshootingEventBroadcaster broadcaster,
            TroubleshootingExecutor executor) {
        this.sessions = sessions;
        this.tickets = tickets;
        this.snapshotter = snapshotter;
        this.turns = turns;
        this.cannedRuns = cannedRuns;
        this.broadcaster = broadcaster;
        this.executor = executor;
    }

    public record SessionWithEvents(TroubleshootingSession session, ImmutableList<TroubleshootingEvent> events) {}

    public SessionWithEvents get(long ticketId) {
        TroubleshootingSession session = sessions.requireByTicketId(ticketId);
        return new SessionWithEvents(session, sessions.events(session.id()));
    }

    /**
     * Safe to repeat: only the call that creates the session reads the thread and starts the first
     * turn, so two engineers opening it together start it once.
     */
    public SessionWithEvents start(long ticketId) {
        Ticket ticket = tickets.findById(new TicketId(ticketId));
        if (ticket == null) {
            throw new TicketNotFoundForSessionException(ticketId);
        }
        boolean created = sessions.createIfAbsent(ticketId);
        TroubleshootingSession session = sessions.requireByTicketId(ticketId);
        if (created) {
            executor.execute(() -> {
                try {
                    snapshotter.snapshot(session.id(), ticket);
                } finally {
                    turns.requestTurn(session.id(), ticketId, 0);
                }
            });
        }
        return new SessionWithEvents(session, sessions.events(session.id()));
    }

    public TroubleshootingEvent sendMessage(long ticketId, String actor, String text) {
        TroubleshootingSession session = sessions.requireByTicketId(ticketId);
        TroubleshootingEvent event = sessions.append(
                session.id(), TroubleshootingEventType.ENGINEER_MESSAGE, actor, new EngineerMessage(text));
        turns.requestTurn(session.id(), ticketId, event.seq());
        return event;
    }

    public TroubleshootingEvent decide(
            long ticketId, long proposalSeq, String actor, DecisionKind decision, @Nullable String editedText) {
        if (decision == DecisionKind.SUPERSEDED) {
            throw new IllegalArgumentException("Engineers approve or reject; only the router supersedes");
        }
        TroubleshootingSession session = sessions.requireByTicketId(ticketId);
        TroubleshootingEvent proposal = sessions.findEvent(session.id(), proposalSeq);
        if (proposal == null || proposal.type() != TroubleshootingEventType.PROPOSAL) {
            throw new ProposalNotPendingException(proposalSeq);
        }
        String text = editedText == null || editedText.isBlank() ? null : editedText.trim();
        TroubleshootingEvent event = sessions.decide(session.id(), proposalSeq, actor, decision, text);
        if (decision == DecisionKind.APPROVED) {
            RouterAction action = RouterAction.valueOf(proposal.text("action"));
            cannedRuns.approved(
                    session.id(), ticketId, action, Objects.requireNonNullElse(text, proposal.text("text")));
        }
        return event;
    }

    public SseEmitter stream(long ticketId, long afterSeq, Function<TroubleshootingEvent, Object> toWire) {
        TroubleshootingSession session = sessions.requireByTicketId(ticketId);
        return broadcaster.subscribe(session.id(), afterSeq, toWire);
    }
}
