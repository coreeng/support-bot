package com.coreeng.supportbot.escalation;

import com.coreeng.supportbot.EnumerationValue;
import com.coreeng.supportbot.slack.MessageTs;
import lombok.Builder;
import lombok.Value;

import javax.annotation.Nullable;

@Value
@Builder(toBuilder = true)
public class Escalation {
    @Nullable
    EscalationId id;
    String channelId;
    EscalationStatus status;
    MessageTs threadTs;

    @Nullable
    EnumerationValue topic;
    @Nullable
    EnumerationValue team;

    public static Escalation createNew(MessageTs threadTs, String channelId) {
        return Escalation.builder()
            .id(EscalationId.createNew())
            .status(EscalationStatus.creating)
            .threadTs(threadTs)
            .channelId(channelId)
            .build();
    }
}
