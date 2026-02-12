package com.coreeng.supportbot.teams.rest;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record TeamUI(
        @JsonProperty("label") String label,
        @JsonProperty("code") String code,
        @JsonProperty("types") List<String> types) {}
