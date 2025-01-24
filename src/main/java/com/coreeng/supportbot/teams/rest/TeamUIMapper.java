package com.coreeng.supportbot.teams.rest;

import com.coreeng.supportbot.teams.PlatformTeam;
import com.coreeng.supportbot.teams.PlatformUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.google.common.collect.ImmutableList.toImmutableList;

@Component
@RequiredArgsConstructor
public class TeamUIMapper {

    public UserUI mapToUI(PlatformUser user) {
        return new UserUI(
            user.email(),
            user.teams().stream()
                .map(this::mapToUI)
                .collect(toImmutableList())
        );
    }

    public TeamUI mapToUI(PlatformTeam team) {
        return new TeamUI(
            team.name()
        );
    }
}
