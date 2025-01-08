package com.coreeng.supportbot.escalation;

import com.coreeng.supportbot.slack.MessageTs;

import javax.annotation.Nullable;

public interface EscalationRepository {
    @Nullable
    Escalation createIfNotExists(Escalation escalation);
    Escalation update(Escalation escalation);

    @Nullable
    Escalation findByThreadTs(MessageTs threadTs);
}
