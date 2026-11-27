package com.coreeng.supportbot.docanalysis;

import java.util.Locale;

public enum DocClassification {
    CORRECT,
    MISSING,
    INCORRECT,
    NOT_APPLICABLE;

    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static DocClassification fromCode(String code) {
        return valueOf(code.toUpperCase(Locale.ROOT));
    }
}
