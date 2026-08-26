package com.coreeng.supportbot.analysis;

import java.time.LocalDate;

/**
 * Resumes a windowed refresh left behind by a restart.
 *
 * <p>The windowed job is owned by the Support Summary feature, but the {@code async_job} row it
 * holds is the shared {@code "analysis"} lock, and {@link AnalysisService} is what inspects that row
 * on startup. This interface is the one-way hook that lets it hand such a job back without the
 * analysis package depending on the summary package; when the summary feature is disabled no
 * implementation exists and the stale row is simply deleted, which is the whole point — otherwise a
 * leftover window job would hold the lock forever.
 */
public interface WindowAnalysisRunner {

    /**
     * Runs (asynchronously) a full refresh for the window: backfill classification gaps, then
     * regenerate the prose summary. The implementation is responsible for releasing the
     * {@code async_job} lock when it finishes.
     */
    void runWindowRefresh(LocalDate from, LocalDate to);
}
