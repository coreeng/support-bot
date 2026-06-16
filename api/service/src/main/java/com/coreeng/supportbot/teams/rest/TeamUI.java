package com.coreeng.supportbot.teams.rest;

import com.coreeng.supportbot.teams.TeamType;
import com.google.common.collect.ImmutableList;

public record TeamUI(String label, String code, ImmutableList<TeamType> types, boolean active) {
    /** Active team (the common case); retired teams use the 4-arg form with active=false (PT-518). */
    public TeamUI(String label, String code, ImmutableList<TeamType> types) {
        this(label, code, types, true);
    }
}
