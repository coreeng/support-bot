package com.coreeng.supportbot.teams.rest;

import com.coreeng.supportbot.teams.PlatformUser;
import com.coreeng.supportbot.teams.Team;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.google.common.collect.ImmutableList.toImmutableList;

@Component
@RequiredArgsConstructor
public class TeamUIMapper {

    public UserUI mapToUI(PlatformUser user, ImmutableList<Team> teams) {
        return new UserUI(
            user.email(),
            teams.stream()
                .map(this::mapToUI)
                .collect(toImmutableList())
        );
    }

    public TeamUI mapToUI(Team team) {
        return new TeamUI(
            team.label(),
            team.code(),
            team.types()
        );
    }
}
