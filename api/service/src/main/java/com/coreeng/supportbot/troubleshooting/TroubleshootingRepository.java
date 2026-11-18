package com.coreeng.supportbot.troubleshooting;

import com.google.common.collect.ImmutableList;
import org.jspecify.annotations.Nullable;

public interface TroubleshootingRepository {
    @Nullable TroubleshootingSession findByTicketId(long ticketId);

    /** Creates the ticket's session unless one exists. Returns true when this call created it. */
    boolean createIfAbsent(long ticketId);

    /** Locks the session row until the surrounding transaction ends. */
    TroubleshootingSession lockSession(long sessionId);

    /** Assigns the next sequence number under the session row lock and stores the event. */
    TroubleshootingEvent append(long sessionId, TroubleshootingEventType type, String actor, Object payload);

    ImmutableList<TroubleshootingEvent> findEventsAfter(long sessionId, long afterSeq);

    @Nullable TroubleshootingEvent findEvent(long sessionId, long seq);

    /** Proposals with no decision yet, oldest first. */
    ImmutableList<TroubleshootingEvent> findPendingProposals(long sessionId);
}
