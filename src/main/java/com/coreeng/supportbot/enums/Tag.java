package com.coreeng.supportbot.enums;

import com.coreeng.supportbot.config.EnumerationValue;

public record Tag(
    String label,
    String code
) implements EnumerationValue {
}
