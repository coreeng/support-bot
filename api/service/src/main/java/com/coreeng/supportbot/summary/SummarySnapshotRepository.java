package com.coreeng.supportbot.summary;

import org.jspecify.annotations.Nullable;

/** Store for the cached prose summaries, keyed on (window, team group, summary prompt version). */
public interface SummarySnapshotRepository {

    /**
     * @return the cached snapshot for this scope and prompt, or null if none has been generated.
     *     A returned snapshot may still be stale — compare its fingerprint before serving it.
     */
    @Nullable SummarySnapshot find(SummaryScope scope, String promptId);

    /** Inserts the snapshot, replacing any existing one for the same window, team group and prompt. */
    void upsert(SummarySnapshot snapshot);
}
