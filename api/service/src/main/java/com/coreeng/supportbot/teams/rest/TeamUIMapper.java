package com.coreeng.supportbot.teams.rest;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.coreeng.supportbot.teams.PlatformUser;
import com.coreeng.supportbot.teams.Team;
import com.coreeng.supportbot.teams.TeamDisplay;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TeamUIMapper {

    public UserUI mapToUI(PlatformUser user, ImmutableList<Team> teams) {
        return new UserUI(user.email(), teams.stream().map(this::mapToUI).collect(toImmutableList()));
    }

    public TeamUI mapToUI(Team team) {
        return new TeamUI(team.label(), team.code(), team.types());
    }

    public TeamUI mapToUI(TeamDisplay team) {
        if (team.active()) {
            return mapToUI(new Team(team.label(), team.code(), team.types()));
        }
        return new TeamUI(team.label(), team.code(), team.types(), false);
    }
}
