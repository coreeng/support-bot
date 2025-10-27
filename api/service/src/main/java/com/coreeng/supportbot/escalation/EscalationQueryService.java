package com.coreeng.supportbot.escalation;

import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.ticket.TicketId;
import com.coreeng.supportbot.util.Page;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Nullable;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
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

    public boolean existsByTicketId(TicketId ticketId) {
        return repository.existsByTicketId(ticketId);
    }

    public long countNotResolvedByTicketId(TicketId ticketId) {
        return repository.countNotResolvedByTicketId(ticketId);
    }
}
