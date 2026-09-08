package com.coreeng.supportbot.summary;

import org.jspecify.annotations.Nullable;

/**
 * What the in-flight refresh, if any, is doing.
 *
 * @param window the window being refreshed, null when idle
 * @param phase the stage the run has reached
 * @param running false when nothing is in flight
 */
public record SummaryRefreshStatus(@Nullable SummaryWindow window, SummaryState.Phase phase, boolean running) {

    public static final SummaryRefreshStatus IDLE =
            new SummaryRefreshStatus(null, SummaryState.Phase.CLASSIFYING, false);
}
