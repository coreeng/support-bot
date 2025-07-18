package com.coreeng.supportbot.teams.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record TeamUI(
    @JsonProperty("name") String name,
    @JsonProperty("types") List<String> types
) {}
