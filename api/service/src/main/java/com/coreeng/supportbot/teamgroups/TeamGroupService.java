package com.coreeng.supportbot.teamgroups;

import com.coreeng.supportbot.config.TeamGroupsProps;
import com.coreeng.supportbot.teams.TeamService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/** Lookup over the team groups, and the display noun the UI calls them by. */
@Service
@RequiredArgsConstructor
public class TeamGroupService {

    private final TeamGroupProvider teamGroupProvider;
    private final TeamGroupsProps props;
    private final TeamService teamService;

    /** A group's member team, with its display label. */
    public record Member(String code, String label) {}

    /** A group with its members' display labels resolved, in the group's member order. */
    public record DescribedTeamGroup(TeamGroup group, ImmutableList<Member> members) {}

    /** The noun the UI shows for a group, e.g. "Domain". */
    public String displayName() {
        return props.displayName();
    }

    /** Every group, in display order; see {@link TeamGroupProvider#groups()}. */
    public ImmutableList<TeamGroup> list() {
        return teamGroupProvider.groups();
    }

    /**
     * @return the group with this code, or null when there is none
     * @throws IllegalArgumentException when two groups share a code, which would make one unreachable
     */
    public @Nullable TeamGroup find(String code) {
        return Maps.uniqueIndex(list(), TeamGroup::code).get(code);
    }

    /** Every group with its members' display labels. */
    public ImmutableList<DescribedTeamGroup> describe() {
        return list().stream()
                .map(group -> new DescribedTeamGroup(
                        group,
                        group.teamCodes().stream()
                                .map(code -> new Member(
                                        code,
                                        teamService.resolveForDisplay(code).label()))
                                .collect(ImmutableList.toImmutableList())))
                .collect(ImmutableList.toImmutableList());
    }
}
