package com.coreeng.supportbot.teams.rest;

import com.coreeng.supportbot.teams.Team;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TeamUIMapper {

    public TeamUI mapToUI(Team team) {
        return new TeamUI(
                team.label(),
                team.code(),
                team.types()
        );
    }
}
