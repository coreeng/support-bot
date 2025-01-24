package com.coreeng.supportbot.enums;

import com.coreeng.supportbot.config.EnumerationValue;

public record EscalationTeam(
    String name,
    String code,
    String slackGroupId
) implements EnumerationValue {
}
