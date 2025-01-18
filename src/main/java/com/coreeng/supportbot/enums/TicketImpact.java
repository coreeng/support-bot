package com.coreeng.supportbot.enums;

import com.coreeng.supportbot.config.EnumerationValue;

public record TicketImpact(
    String name,
    String code
) implements EnumerationValue {
}
