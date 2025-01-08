package com.coreeng.supportbot.escalation;

import com.coreeng.supportbot.slack.MessageTs;
import org.springframework.stereotype.Component;

import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.google.common.base.Preconditions.checkNotNull;

@Component
public class EscalationInMemoryRepository implements EscalationRepository {
    private final ConcurrentMap<EscalationId, Escalation> escalations = new ConcurrentHashMap<>();
    private final ConcurrentMap<MessageTs, Escalation> escalationsByThreadTs = new ConcurrentHashMap<>();

    @Nullable
    @Override
    public Escalation createIfNotExists(Escalation escalation) {
        checkNotNull(escalation);

        escalationsByThreadTs.computeIfAbsent(escalation.threadTs(), (threadTs) -> {
            escalations.put(escalation.id(), escalation);
            return escalation;
        });
        return escalations.get(escalation.id());
    }

    @Override
    public Escalation update(Escalation escalation) {
        checkNotNull(escalation);

        return escalationsByThreadTs.computeIfPresent(escalation.threadTs(), (key, e) -> {
            escalations.put(escalation.id(), escalation);
            return escalation;
        });
    }

    @Nullable
    @Override
    public Escalation findByThreadTs(MessageTs threadTs) {
        return escalationsByThreadTs.get(threadTs);
    }
}
