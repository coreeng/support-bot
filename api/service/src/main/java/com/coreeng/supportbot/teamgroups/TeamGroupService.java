package com.coreeng.supportbot.teamgroups;

import com.coreeng.supportbot.config.TeamGroupsProps;
import com.coreeng.supportbot.teams.Team;
import com.coreeng.supportbot.teams.TeamService;
import com.coreeng.supportbot.teams.TeamType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/** Lookup over the configured team groups, and the display noun the UI calls them by. */
@Service
@RequiredArgsConstructor
@Slf4j
public class TeamGroupService {

    private final TeamGroupProvider teamGroupProvider;
    private final TeamGroupsProps props;
    private final TeamService teamService;

    /** A group's member team, with its display label. */
    public record Member(String code, String label) {}

    /** A group with its members' display labels resolved. */
    public record DescribedTeamGroup(TeamGroup group, ImmutableList<Member> members) {}

    /** The noun the UI shows for a group, e.g. "Area". */
    public String displayName() {
        return props.displayName();
    }

    /** Every configured group, in display order. */
    public ImmutableList<TeamGroup> list() {
        return teamGroupProvider.groups();
    }

    /** @return the group with this code, or null when none is configured under it */
    public @Nullable TeamGroup find(String code) {
        return list().stream()
                .filter(group -> group.code().equals(code))
                .findFirst()
                .orElse(null);
    }

    /**
     * Every group with its members' labels. A member that is no longer a configured team still shows,
     * under the label it last had, or its code if it never had one.
     */
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

    /**
     * Warns about group members that are not current tenant teams. Deliberately not fatal: a retired
     * team's code is still on its historical tickets, listing it is how that history stays in the
     * group, and team providers that read from Kubernetes can drift independently of this config.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warnAboutUnknownTeams() {
        ImmutableList<TeamGroup> groups = list();
        if (groups.isEmpty()) {
            return;
        }
        ImmutableSet<String> tenantCodes = teamService.listTeamsByType(TeamType.TENANT).stream()
                .map(Team::code)
                .collect(ImmutableSet.toImmutableSet());
        for (TeamGroup group : groups) {
            List<String> unknown = group.teamCodes().stream()
                    .filter(code -> !tenantCodes.contains(code))
                    .toList();
            if (unknown.size() == group.teamCodes().size()) {
                log.warn(
                        "Team group '{}' has no current tenant team among its members {}; its summary will only"
                                + " show tickets still attributed to those codes",
                        group.code(),
                        unknown);
            } else if (!unknown.isEmpty()) {
                log.warn(
                        "Team group '{}' lists team codes that are not current tenant teams: {}. Retired teams are"
                                + " fine to keep for history; otherwise check for a typo",
                        group.code(),
                        unknown);
            }
        }
    }
}
