package com.coreeng.supportbot.analysis;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The kinds of prompt stored in {@code analysis_prompt}.
 *
 * <p>Each type is versioned independently and has at most one row flagged {@code is_in_use},
 * enforced by the per-type partial unique index added in V38.
 */
@Getter
@RequiredArgsConstructor
public enum AnalysisPromptType {

    /** Per-ticket classifier: drives {@code analysis.driver/category/feature/summary}. */
    CLASSIFICATION("classification"),

    /** Windowed prose summary rendered on the Support Summary page. */
    SUMMARY("summary");

    /** The value stored in {@code analysis_prompt.type}. */
    private final String dbValue;
}
