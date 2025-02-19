package com.coreeng.supportbot.escalation;

import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.util.Page;
import com.google.common.collect.ImmutableList;

import javax.annotation.Nullable;
import java.time.Instant;

public interface EscalationRepository {
    @Nullable
    Escalation createIfNotExists(Escalation escalation);

    Escalation update(Escalation escalation);

    Escalation markResolved(Escalation escalation, Instant at);

    @Nullable
    Escalation findById(EscalationId id);
    boolean existsByThreadTs(MessageTs threadTs);

    ImmutableList<Escalation> listByTicketId(TicketId ticketId);

    long countNotResolvedByTicketId(TicketId ticketId);

    Page<Escalation> findByQuery(EscalationQuery query);
}
