package com.coreeng.supportbot.teamgroups;

import com.coreeng.supportbot.config.TeamGroupsProps;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import org.springframework.stereotype.Component;

/** Team groups listed under {@code team-groups.groups} in configuration. */
@Component
public class StaticTeamGroupProvider implements TeamGroupProvider {

    private final ImmutableList<TeamGroup> groups;

    public StaticTeamGroupProvider(TeamGroupsProps props) {
        this.groups = props.groups().stream()
                .map(group -> new TeamGroup(group.code(), group.label(), ImmutableSortedSet.copyOf(group.teamCodes())))
                .collect(ImmutableList.toImmutableList());
    }

    @Override
    public ImmutableList<TeamGroup> groups() {
        return groups;
    }
}
