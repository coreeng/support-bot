package com.coreeng.supportbot.analysis;

import java.time.LocalDate;

/**
 * Resumes a windowed refresh left behind by a restart.
 *
 * <p>The windowed job is owned by the Support Summary feature, but the {@code async_job} row it
 * holds is the shared {@link AnalysisJobData#JOB_ID analysis} lock, and {@link AnalysisJobResumer}
 * is what inspects that row on startup. This interface is the one-way hook that lets it hand such a
 * job back without the analysis package depending on the summary package; when the summary feature
 * is disabled no implementation exists and the stale row is simply deleted, which is the whole point
 * — otherwise a leftover window job would hold the lock forever.
 */
public interface WindowAnalysisRunner {

    /**
     * Dispatches a full refresh for the window — backfill classification gaps, then regenerate the
     * prose summary — onto the analysis executor. The caller must already hold the {@code async_job}
     * lock; the implementation releases it when the run finishes, or right away if it cannot be
     * dispatched.
     *
     * @return false when the executor rejected the run; the lock row has then been released
     */
    boolean runWindowRefresh(LocalDate from, LocalDate to);
}
