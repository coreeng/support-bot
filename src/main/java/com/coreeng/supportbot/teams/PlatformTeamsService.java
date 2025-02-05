package com.coreeng.supportbot.teams;

import com.coreeng.supportbot.enums.EscalationTeam;
import com.coreeng.supportbot.enums.EscalationTeamsRegistry;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.stream.Collectors.joining;

@RequiredArgsConstructor
@Slf4j
public class PlatformTeamsService {
    private final PlatformTeamsFetcher teamsFetcher;
    private final PlatformUsersFetcher usersFetcher;
    private final EscalationTeamsRegistry escalationTeamsRegistry;

    private final Map<String, PlatformUser> usersByEmail = new HashMap<>();
    private final Map<String, PlatformTeam> teamByName = new HashMap<>();
    private final Map<String, List<PlatformUser>> groupIdToUsers = new HashMap<>();

    @PostConstruct
    void init() {
        List<PlatformTeamsFetcher.TeamAndGroupTuple> teams = teamsFetcher.fetchTeams();
        validateEscalationTeamsMapping(teams);
        for (var t : teams) {
            PlatformTeam team = teamByName.computeIfAbsent(t.name(), k -> new PlatformTeam(
                t.name(),
                new HashSet<>(),
                new HashSet<>()
            ));
            team.groupRefs().add(t.groupRef());

            if (groupIdToUsers.containsKey(t.groupRef())) {
                List<PlatformUser> users = groupIdToUsers.get(t.groupRef());
                team.users().addAll(users);
                for (PlatformUser user : users) {
                    user.teams().add(team);
                }
                continue;
            }

            List<PlatformUsersFetcher.Membership> memberships = usersFetcher.fetchMembershipsByGroupRef(t.groupRef());
            List<PlatformUser> users = new ArrayList<>();
            for (PlatformUsersFetcher.Membership m : memberships) {
                PlatformUser user = usersByEmail.computeIfAbsent(m.email(), k -> new PlatformUser(
                    m.email(),
                    new HashSet<>()
                ));
                users.add(user);
            }

            groupIdToUsers.put(t.groupRef(), users);
            for (PlatformUser user : users) {
                team.users().add(user);
                user.teams().add(team);
            }
        }

        log.atInfo()
            .addArgument(teamByName::size)
            .addArgument(groupIdToUsers::size)
            .addArgument(usersByEmail::size)
            .log("Finished fetching teams info. Teams({}), Groups({}), Users({})");
    }

    private void validateEscalationTeamsMapping(List<PlatformTeamsFetcher.TeamAndGroupTuple> teams) {
        ImmutableSet<String> teamNames = teams.stream()
            .map(PlatformTeamsFetcher.TeamAndGroupTuple::name)
            .collect(toImmutableSet());
        ImmutableSet<String> escalationTeamNames = escalationTeamsRegistry.listAllEscalationTeams().stream()
            .map(EscalationTeam::name)
            .collect(toImmutableSet());
        var setsDiff = Sets.difference(escalationTeamNames, teamNames);
        if (!setsDiff.isEmpty()) {
            throw new IllegalStateException("Unknown escalation teams specified: " +
                setsDiff.stream()
                    .collect(joining(", ", "[", "]"))
            );
        }
    }

    public ImmutableList<PlatformTeam> listTeams() {
        return ImmutableList.copyOf(teamByName.values());
    }

    public ImmutableList<PlatformTeam> listTeamsByUserEmail(String email) {
        PlatformUser user = usersByEmail.get(email);
        if (user == null) {
            return ImmutableList.of();
        }
        return ImmutableList.copyOf(user.teams());
    }

    @Nullable
    public PlatformTeam findTeamByName(String name) {
        return teamByName.get(name);
    }

    @Nullable
    public PlatformUser findUserByEmail(String email) {
        return usersByEmail.get(email);
    }
}
