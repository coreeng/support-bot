package com.coreeng.supportbot.teams;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Slf4j
public class PlatformTeamsService {
    private final TeamsFetcher teamsFetcher;
    private final UsersFetcher usersFetcher;

    private final Map<String, PlatformUser> usersByEmail = new HashMap<>();
    private final Map<String, PlatformTeam> teamByName = new HashMap<>();
    private final Map<String, List<PlatformUser>> groupIdToUsers = new HashMap<>();

    @PostConstruct
    void init() {
        List<TeamsFetcher.TeamAndGroupTuple> teams = teamsFetcher.fetchTeams();
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

            List<UsersFetcher.Membership> memberships = usersFetcher.fetchMembershipsByGroupRef(t.groupRef());
            List<PlatformUser> users = new ArrayList<>();
            for (UsersFetcher.Membership m : memberships) {
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
}
