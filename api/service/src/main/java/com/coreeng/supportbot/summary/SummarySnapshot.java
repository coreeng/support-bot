package com.coreeng.supportbot.summary;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * A cached prose summary for one window under one prompt version.
 *
 * @param window the window the prose describes
 * @param promptId SHA-256 of the summary prompt used; a new prompt version means a new cache entry
 *     rather than an overwrite
 * @param fingerprint the {@link SummaryFingerprint#value()} of the analysis rows it was generated
 *     from — the prose is served as-is only while this still matches
 * @param content the generated prose
 * @param model the LLM model name that produced it, for traceability
 * @param generatedAt when it was generated; null on a value not yet persisted
 */
public record SummarySnapshot(
        SummaryWindow window,
        String promptId,
        String fingerprint,
        String content,
        String model,
        @Nullable Instant generatedAt) {}
