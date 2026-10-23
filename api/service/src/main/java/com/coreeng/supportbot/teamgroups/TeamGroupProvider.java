package com.coreeng.supportbot.teamgroups;

import com.google.common.collect.ImmutableList;

/**
 * Where team groups come from. Today they are built from a team attribute ({@link
 * AttributeTeamGroupProvider}); this is the seam for another source.
 */
public interface TeamGroupProvider {

    /** Every group, in display order. Codes are unique. */
    ImmutableList<TeamGroup> groups();
}
