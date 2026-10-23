package com.coreeng.supportbot.summary;

import com.coreeng.supportbot.teamgroups.TeamGroup;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;

/**
 * What a Support Summary covers: a window, optionally narrowed to one team group's tickets.
 *
 * <p>Kept apart from {@link SummaryWindow} on purpose. Classification is never scoped — the backfill
 * and the {@code async_job} lock work on the whole window, so every ticket is classified whichever
 * group is being looked at, and switching group only costs the prose. Those paths take the window;
 * the breakdowns, the fingerprint, the cached prose and the failure memory take the scope.
 *
 * @param teamGroup the group the summary is narrowed to; null for every ticket in the window ("All")
 */
public record SummaryScope(SummaryWindow window, @Nullable TeamGroup teamGroup) {

    /** Stored in {@code summary_snapshot.team_group} for a summary of the whole window. */
    public static final String ALL_KEY = "";

    public static SummaryScope all(SummaryWindow window) {
        return new SummaryScope(window, null);
    }

    public boolean isAll() {
        return teamGroup == null;
    }

    /** The group's code, or {@link #ALL_KEY} for the whole window. */
    public String teamGroupKey() {
        return teamGroup == null ? ALL_KEY : teamGroup.code();
    }

    /** The team codes the scope is limited to; empty for the whole window. */
    public ImmutableSortedSet<String> teamCodes() {
        return teamGroup == null ? ImmutableSortedSet.of() : teamGroup.teamCodes();
    }

    /**
     * Digest of the group's definition (label and members), so a cached summary goes stale when the
     * group's label or members change even if no ticket moves; null for the whole window, whose fingerprint
     * must stay exactly what it was before groups existed.
     */
    public @Nullable String membershipDigest() {
        if (teamGroup == null) {
            return null;
        }
        String definition = teamGroup.label() + "\n" + String.join(",", teamGroup.teamCodes());
        return Hashing.sha256()
                .hashString(definition, StandardCharsets.UTF_8)
                .toString()
                .substring(0, 16);
    }

    /** For logs: {@code 2026-01-01..2026-01-14}, with {@code [team group x]} when narrowed. */
    public String describe() {
        String range = window.from() + ".." + window.to();
        return teamGroup == null ? range : range + " [team group " + teamGroup.code() + "]";
    }
}
