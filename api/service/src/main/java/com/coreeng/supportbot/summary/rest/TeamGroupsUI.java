package com.coreeng.supportbot.summary.rest;

import java.util.List;

/**
 * The team groups the Support Summary page can be narrowed to.
 *
 * @param displayName the noun the deployment calls a group by, e.g. "Area"
 * @param groups the configured groups, in display order; empty when none are configured
 */
public record TeamGroupsUI(String displayName, List<TeamGroupUI> groups) {

    /** @param teams the group's member teams, with display labels */
    public record TeamGroupUI(String code, String label, List<MemberUI> teams) {}

    public record MemberUI(String code, String label) {}
}
