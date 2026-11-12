package com.coreeng.supportbot.summary;

import com.google.common.collect.ImmutableList;

/**
 * The per-ticket reason lines a summary's report carries, and how many there were to choose from.
 *
 * @param lines the reasons sent to the model, newest ticket first
 * @param available how many non-blank reasons the scope had; more than {@code lines} when the
 *     report holds a sample of them
 */
public record SummaryReasons(ImmutableList<SummaryReason> lines, int available) {

    /** Every reason the scope has, with none left out. */
    public static SummaryReasons all(ImmutableList<SummaryReason> lines) {
        return new SummaryReasons(lines, lines.size());
    }

    /** Whether the lines are a sample, so the report should say so. */
    public boolean sampled() {
        return lines.size() < available;
    }
}
