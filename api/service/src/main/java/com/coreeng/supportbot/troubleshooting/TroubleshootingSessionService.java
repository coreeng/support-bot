package com.coreeng.supportbot.troubleshooting;

import com.coreeng.supportbot.troubleshooting.Payloads.Decision;
import com.coreeng.supportbot.troubleshooting.Payloads.DecisionKind;
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

    /**
     * First decision wins: the "still waiting" check runs under the session row lock that appends
     * also take, so a second decision, or one on a proposal a newer turn replaced, is refused.
     */
    public TroubleshootingEvent decide(
            long sessionId, long proposalSeq, String actor, DecisionKind decision, @Nullable String text) {
        TroubleshootingEvent event = Objects.requireNonNull(transactions.execute(status -> {
            repository.lockSession(sessionId);
            boolean pending = repository.findPendingProposals(sessionId).stream()
                    .anyMatch(proposal -> proposal.seq() == proposalSeq);
            if (!pending) {
                throw new ProposalNotPendingException(proposalSeq);
            }
            return repository.append(
                    sessionId, TroubleshootingEventType.DECISION, actor, new Decision(proposalSeq, decision, text));
        }));
        broadcaster.eventsAppended(sessionId);
        return event;
    }

    /** Appends a router result, first marking any proposal still waiting as replaced. */
    public TroubleshootingEvent appendRouterOutput(long sessionId, TroubleshootingEventType type, Object payload) {
        TroubleshootingEvent event = Objects.requireNonNull(transactions.execute(status -> {
            repository.lockSession(sessionId);
            for (TroubleshootingEvent proposal : repository.findPendingProposals(sessionId)) {
                repository.append(
                        sessionId,
                        TroubleshootingEventType.DECISION,
                        Actors.ROUTER,
                        new Decision(proposal.seq(), DecisionKind.SUPERSEDED, null));
            }
            return repository.append(sessionId, type, Actors.ROUTER, payload);
        }));
        broadcaster.eventsAppended(sessionId);
        return event;
    }

    public @Nullable TroubleshootingEvent findEvent(long sessionId, long seq) {
        return repository.findEvent(sessionId, seq);
    }

    public ImmutableList<TroubleshootingEvent> eventsAfter(long sessionId, long afterSeq) {
        return repository.findEventsAfter(sessionId, afterSeq);
    }
}
