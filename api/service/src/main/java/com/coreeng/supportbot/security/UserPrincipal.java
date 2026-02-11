package com.coreeng.supportbot.security;

import com.coreeng.supportbot.teams.Team;
import com.google.common.collect.ImmutableList;

public record UserPrincipal(String email, String name, ImmutableList<Team> teams, ImmutableList<Role> roles) {

    public boolean isLeadership() {
        return roles.contains(Role.LEADERSHIP);
    }

    public boolean isSupportEngineer() {
        return roles.contains(Role.SUPPORT_ENGINEER);
    }

    public boolean isEscalation() {
        return roles.contains(Role.ESCALATION);
    }
}
