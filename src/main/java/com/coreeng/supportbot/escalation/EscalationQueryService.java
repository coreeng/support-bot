package com.coreeng.supportbot.escalation;

import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.util.Page;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;

@Service
@RequiredArgsConstructor
public class EscalationQueryService {
    private final EscalationRepository repository;

    public Page<Escalation> findByQuery(EscalationQuery query) {
        return repository.findByQuery(query);
    }

    public ImmutableList<Escalation> listByTicketId(TicketId ticketId) {
        return repository.listByTicketId(ticketId);
    }

    @Nullable
    public Escalation findById(EscalationId escalationId) {
        return repository.findById(escalationId);
    }

    public boolean existsByThreadTs(MessageTs threadTs) {
        return repository.existsByThreadTs(threadTs);
    }

    public long countNotResolvedByTicketId(TicketId ticketId) {
        return repository.countNotResolvedByTicketId(ticketId);
    }
}
