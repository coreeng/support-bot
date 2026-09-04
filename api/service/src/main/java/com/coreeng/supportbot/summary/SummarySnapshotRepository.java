package com.coreeng.supportbot.summary;

import org.jspecify.annotations.Nullable;

/** Store for the cached prose summaries, keyed on (window, summary prompt version). */
public interface SummarySnapshotRepository {

    /**
     * @return the cached snapshot for this window and prompt, or null if none has been generated.
     *     A returned snapshot may still be stale — compare its fingerprint before serving it.
     */
    @Nullable SummarySnapshot find(SummaryWindow window, String promptId);

    /** Inserts the snapshot, replacing any existing one for the same window and prompt. */
    void upsert(SummarySnapshot snapshot);
}
