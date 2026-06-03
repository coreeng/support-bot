package com.coreeng.supportbot.prtracking.source;

import java.util.Locale;

public enum Provider {
    GITHUB,
    GITLAB;

    /**
     * Lowercase form used for the {@code pr_tracking.provider} column and for YAML config matching.
     * Centralised so we have one source of truth — never call {@code .name().toLowerCase()} at the
     * call site or you risk inconsistent casing if this enum ever gains a multi-word variant.
     */
    public String storageValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Parses a stored value (case-insensitive) back to the enum. Throws on unknown providers. */
    public static Provider fromStorage(String value) {
        return Provider.valueOf(value.toUpperCase(Locale.ROOT));
    }
}
