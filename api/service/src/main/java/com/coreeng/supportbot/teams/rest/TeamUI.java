package com.coreeng.supportbot.teams.rest;

import com.coreeng.supportbot.teams.TeamType;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/** @param attributes omitted from JSON when empty, so tickets and escalations that embed a team are unchanged */
public record TeamUI(
        String label,
        String code,
        ImmutableList<TeamType> types,
        boolean active,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) ImmutableMap<String, String> attributes) {
    public TeamUI(String label, String code, ImmutableList<TeamType> types, boolean active) {
        this(label, code, types, active, ImmutableMap.of());
    }

    public TeamUI(String label, String code, ImmutableList<TeamType> types) {
        this(label, code, types, true);
    }
}
