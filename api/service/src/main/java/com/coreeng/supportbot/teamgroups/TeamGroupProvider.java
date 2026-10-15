package com.coreeng.supportbot.teamgroups;

import com.google.common.collect.ImmutableList;

/**
 * Where team groups come from. Configuration is the only source today ({@link
 * StaticTeamGroupProvider}); this is the seam for one that derives them from somewhere else, such as
 * a label on the tenant's Kubernetes namespace.
 */
public interface TeamGroupProvider {

    /** Every configured group, in display order. */
    ImmutableList<TeamGroup> groups();
}
