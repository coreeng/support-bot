package com.coreeng.supportbot.analysis;

import org.jspecify.annotations.Nullable;

/** A persisted analysis result and the prompt version that produced it. */
public record AnalysisRecord(
        int ticketId,
        @Nullable String driver,
        @Nullable String category,
        @Nullable String feature,
        @Nullable String summary,
        @Nullable String promptId) {

    public boolean isValid() {
        return ticketId > 0 && isValid(driver) && isValid(category) && isValid(feature) && isValid(summary);
    }

    private boolean isValid(@Nullable String value) {
        return value != null && !value.isBlank();
    }
}
