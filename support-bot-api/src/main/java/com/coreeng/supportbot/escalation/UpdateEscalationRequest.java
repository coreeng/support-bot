package com.coreeng.supportbot.escalation;

import com.coreeng.supportbot.config.EnumerationValue;
import com.coreeng.supportbot.slack.MessageRef;

import javax.annotation.Nullable;

public record UpdateEscalationRequest(
    MessageRef messageRef,
    @Nullable EnumerationValue topic,
    @Nullable EnumerationValue team
) {
}
