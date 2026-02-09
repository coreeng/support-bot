package com.coreeng.supportbot.security;

import com.coreeng.supportbot.teams.Team;
import com.google.common.collect.ImmutableList;

public record UserPrincipal(
    String email,
    String name,
    ImmutableList<Team> teams,
    boolean isLeadership,
    boolean isSupportEngineer,
    boolean isEscalation
) {}
