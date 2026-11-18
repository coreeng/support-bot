package com.coreeng.supportbot.troubleshooting;

import com.coreeng.supportbot.ticket.Ticket;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.ticket.TicketQueryService;
import com.google.common.collect.ImmutableList;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
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
    private final TroubleshootingEventBroadcaster broadcaster;
    private final TroubleshootingExecutor executor;

    public TroubleshootingService(
            TroubleshootingSessionService sessions,
            TicketQueryService tickets,
            SlackThreadSnapshotter snapshotter,
            TroubleshootingEventBroadcaster broadcaster,
            TroubleshootingExecutor executor) {
        this.sessions = sessions;
        this.tickets = tickets;
        this.snapshotter = snapshotter;
        this.broadcaster = broadcaster;
        this.executor = executor;
    }

    public record SessionWithEvents(TroubleshootingSession session, ImmutableList<TroubleshootingEvent> events) {}

    public SessionWithEvents get(long ticketId) {
        TroubleshootingSession session = sessions.requireByTicketId(ticketId);
        return new SessionWithEvents(session, sessions.events(session.id()));
    }

    /**
     * Safe to repeat: only the call that creates the session reads the thread, so two engineers
     * opening it together start it once.
     */
    public SessionWithEvents start(long ticketId) {
        Ticket ticket = tickets.findById(new TicketId(ticketId));
        if (ticket == null) {
            throw new TicketNotFoundForSessionException(ticketId);
        }
        boolean created = sessions.createIfAbsent(ticketId);
        TroubleshootingSession session = sessions.requireByTicketId(ticketId);
        if (created) {
            executor.execute(() -> snapshotter.snapshot(session.id(), ticket));
        }
        return new SessionWithEvents(session, sessions.events(session.id()));
    }

    public SseEmitter stream(long ticketId, long afterSeq, Function<TroubleshootingEvent, Object> toWire) {
        TroubleshootingSession session = sessions.requireByTicketId(ticketId);
        return broadcaster.subscribe(session.id(), afterSeq, toWire);
    }
}
