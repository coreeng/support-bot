package com.coreeng.supportbot.escalation;

import com.coreeng.supportbot.slack.MessageTs;
import com.coreeng.supportbot.ticket.TicketId;
import com.google.common.collect.ImmutableList;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;

@Component
public class EscalationInMemoryRepository implements EscalationRepository {
    private final ConcurrentMap<EscalationId, Escalation> escalations = new ConcurrentHashMap<>();
    private final ConcurrentMap<MessageTs, Escalation> escalationsByThreadTs = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(1);

    @Nullable
    @Override
    public Escalation createIfNotExists(Escalation escalation) {
        checkNotNull(escalation);
        checkArgument(escalation.id() == null);

        Escalation escalationWithId = escalation.toBuilder()
            .id(new EscalationId(idSequence.getAndIncrement()))
            .build();

        if (escalation.threadTs() != null) {
            escalationsByThreadTs.computeIfAbsent(escalation.threadTs(), (threadTs) -> {
                escalations.put(escalationWithId.id(), escalationWithId);
                return escalationWithId;
            });
        } else {
            escalations.put(escalationWithId.id(), escalationWithId);
        }
        return escalations.get(escalationWithId.id());
    }

    @Override
    public Escalation update(Escalation escalation) {
        checkNotNull(escalation);

        if (escalation.threadTs() != null) {
            escalationsByThreadTs.compute(escalation.threadTs(), (key, e) -> {
                escalations.put(escalation.id(), escalation);
                return escalation;
            });
        } else {
            Escalation previousVersion = checkNotNull(escalations.put(escalation.id(), escalation));
            if (previousVersion.threadTs() != null) {
                escalationsByThreadTs.remove(previousVersion.threadTs(), previousVersion);
            }
        }
        return escalations.get(escalation.id());
    }

    @Nullable
    @Override
    public Escalation findById(EscalationId id) {
        return escalations.get(id);
    }

    @Override
    public boolean existsByThreadTs(MessageTs threadTs) {
        return escalationsByThreadTs.containsKey(threadTs);
    }

    @Override
    public ImmutableList<Escalation> listByTicketId(TicketId ticketId) {
        return escalations.values().stream()
            .filter(e -> ticketId.equals(e.ticketId()))
            .collect(toImmutableList());
    }

    @Override
    public long countNotResolvedByTicketId(TicketId ticketId) {
        return escalations.values().stream()
            .filter(e -> ticketId.equals(e.ticketId())
                && e.status() != EscalationStatus.resolved)
            .count();
    }
}
