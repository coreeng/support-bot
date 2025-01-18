package com.coreeng.supportbot.enums;

import com.coreeng.supportbot.config.EnumerationValue;

public record SlackTeam(
    String name,
    String code,
    String id
) implements EnumerationValue {
}
