package com.coreeng.supportbot.analysis;

import org.jspecify.annotations.Nullable;

public interface AnalysisPromptRepository {

    /**
     * Finds the prompt version currently marked {@code is_in_use}. At most one row can hold that
     * flag, enforced by a partial unique index.
     *
     * @return the in-use prompt, or null if no version is marked as in use
     */
    @Nullable AnalysisPrompt findInUse();
}
