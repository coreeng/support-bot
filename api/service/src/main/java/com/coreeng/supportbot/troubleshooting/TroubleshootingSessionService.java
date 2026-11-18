package com.coreeng.supportbot.troubleshooting;

import com.google.common.collect.ImmutableList;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Reads and appends to session logs. Streams are told about new events only after they commit. */
@Service
@ConditionalOnTroubleshootingEnabled
public class TroubleshootingSessionService {
    private final TroubleshootingRepository repository;
    private final TroubleshootingEventBroadcaster broadcaster;
    private final TransactionTemplate transactions;

    public TroubleshootingSessionService(
            TroubleshootingRepository repository,
            TroubleshootingEventBroadcaster broadcaster,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.broadcaster = broadcaster;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public @Nullable TroubleshootingSession findByTicketId(long ticketId) {
        return repository.findByTicketId(ticketId);
    }

    public TroubleshootingSession requireByTicketId(long ticketId) {
        TroubleshootingSession session = repository.findByTicketId(ticketId);
        if (session == null) {
            throw new TroubleshootingSessionNotFoundException(ticketId);
        }
        return session;
    }

    /** Returns true when this call created the session. */
    public boolean createIfAbsent(long ticketId) {
        return repository.createIfAbsent(ticketId);
    }

    public ImmutableList<TroubleshootingEvent> events(long sessionId) {
        return repository.findEventsAfter(sessionId, 0);
    }

    public TroubleshootingEvent append(long sessionId, TroubleshootingEventType type, String actor, Object payload) {
        TroubleshootingEvent event = Objects.requireNonNull(
                transactions.execute(status -> repository.append(sessionId, type, actor, payload)));
        broadcaster.eventsAppended(sessionId);
        return event;
    }

    public ImmutableList<TroubleshootingEvent> eventsAfter(long sessionId, long afterSeq) {
        return repository.findEventsAfter(sessionId, afterSeq);
    }
}
