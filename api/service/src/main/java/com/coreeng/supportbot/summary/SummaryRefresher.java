package com.coreeng.supportbot.summary;

import org.jspecify.annotations.Nullable;

/**
 * The trigger-and-observe half of a summary refresh: what {@link SummaryService} needs while serving
 * a request. The running half is {@link com.coreeng.supportbot.analysis.WindowAnalysisRunner}.
 *
 * <p>The split is not decoration. The implementation is {@code @Async}, so Spring proxies it, and
 * because it already implements {@code WindowAnalysisRunner} that proxy is a JDK dynamic proxy —
 * which exposes only interfaces. Depending on the concrete class from here would fail at context
 * startup with "could not be injected because it is a JDK dynamic proxy". Keeping both sides of the
 * bean behind interfaces makes the wiring proxy-agnostic instead of relying on CGLIB.
 */
public interface SummaryRefresher {

    /**
     * Claims the shared {@code async_job} lock and kicks off a refresh for the window.
     *
     * @return false when another run already holds the lock, or the executor is saturated
     */
    boolean start(SummaryWindow window);

    /** The current refresh state; {@link SummaryRefreshStatus#running()} is false when idle. */
    SummaryRefreshStatus status();

    /**
     * @return the recorded error for this window and data fingerprint, or null when the last attempt
     *     did not fail or the data has moved on since it did
     */
    @Nullable String failureFor(SummaryWindow window, String fingerprint);
}
