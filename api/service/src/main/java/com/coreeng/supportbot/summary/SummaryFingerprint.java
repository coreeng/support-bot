package com.coreeng.supportbot.summary;

import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;

/**
 * Cheap digest of the {@code analysis} rows a window's summary was generated from.
 *
 * <p>Cache validity is decided by comparing this against the stored snapshot's value — there is no
 * timer. Rows are only ever inserted or updated (never deleted), so a change to either the row count
 * or the latest {@code updated_at} means the window's data moved and the prose is stale. This also
 * catches cross-window drift: a backfill triggered from one window can classify tickets that fall in
 * another cached window, and that window's fingerprint changes with it.
 *
 * @param analysisCount number of analysis rows for the window under the current prompt
 * @param maxUpdatedAt the latest {@code analysis.updated_at} among them, null when there are none
 */
public record SummaryFingerprint(
        long analysisCount, @Nullable LocalDateTime maxUpdatedAt) {

    /** Stable string form, stored in {@code summary_snapshot.fingerprint}. */
    public String value() {
        return analysisCount + "@" + (maxUpdatedAt == null ? "-" : maxUpdatedAt.toString());
    }
}
