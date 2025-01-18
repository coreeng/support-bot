package com.coreeng.supportbot.escalation;

import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.ticket.TicketId;
import com.google.common.collect.ImmutableList;

import javax.annotation.Nullable;

public interface EscalationRepository {
    @Nullable
    Escalation createIfNotExists(Escalation escalation);

    Escalation update(Escalation escalation);

    @Nullable
    Escalation findById(EscalationId id);
    boolean existsByThreadTs(MessageTs threadTs);

    ImmutableList<Escalation> listByTicketId(TicketId ticketId);

    long countNotResolvedByTicketId(TicketId ticketId);
}
