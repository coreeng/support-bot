package com.coreeng.supportbot.teams.rest;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UserInfoResponse {
    private String email;
    private List<TeamDto> teams;
    private boolean isLeadership;
    private boolean isSupportEngineer;

    public UserInfoResponse(String email, List<TeamDto> teams, boolean isLeadership, boolean isSupportEngineer) {
        this.email = email;
        this.teams = teams;
        this.isLeadership = isLeadership;
        this.isSupportEngineer = isSupportEngineer;
    }
}
