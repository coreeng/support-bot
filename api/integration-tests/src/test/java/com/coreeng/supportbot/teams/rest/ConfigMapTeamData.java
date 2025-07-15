package com.coreeng.supportbot.teams.rest;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ConfigMapTeamData(
    @JsonProperty("name") String name,
    @JsonProperty("groupRef") String groupRef
) {
}
