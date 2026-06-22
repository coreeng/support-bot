package com.coreeng.supportbot.teams.rest;

import com.coreeng.supportbot.teams.TeamType;
import com.google.common.collect.ImmutableList;

public record TeamUI(String label, String code, ImmutableList<TeamType> types, boolean active) {
    public TeamUI(String label, String code, ImmutableList<TeamType> types) {
        this(label, code, types, true);
    }
}
