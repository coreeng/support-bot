package com.coreeng.supportbot.escalation;

import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.ticket.TicketId;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;

@Service
@RequiredArgsConstructor
public class EscalationQueryService {
    private final EscalationRepository repository;

    public boolean existsByThreadTs(MessageTs threadTs) {
        return repository.existsByThreadTs(threadTs);
    }

    @Nullable
    public Escalation findById(EscalationId escalationId) {
        return repository.findById(escalationId);
    }

    public ImmutableList<Escalation> listByTicketId(TicketId ticketId) {
        return repository.listByTicketId(ticketId);
    }

    public long countNotResolvedByTicketId(TicketId ticketId) {
        return repository.countNotResolvedByTicketId(ticketId);
    }
}
