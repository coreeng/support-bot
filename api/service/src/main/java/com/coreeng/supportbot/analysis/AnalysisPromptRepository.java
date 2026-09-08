package com.coreeng.supportbot.analysis;

import org.jspecify.annotations.Nullable;

public interface AnalysisPromptRepository {

    /**
     * Finds the prompt version currently marked {@code is_in_use} for the given type. At most one row
     * per type can hold that flag, enforced by a partial unique index.
     *
     * @param type the kind of prompt to load
     * @return the in-use prompt of that type, or null if no version is marked as in use
     */
    @Nullable AnalysisPrompt findInUse(AnalysisPromptType type);
}
