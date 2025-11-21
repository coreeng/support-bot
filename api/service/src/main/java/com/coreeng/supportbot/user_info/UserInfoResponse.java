package com.coreeng.supportbot.user_info;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UserInfoResponse {
    private String email;
    private List<TeamDto> teams;

    public UserInfoResponse(String email, List<TeamDto> teams) {
        this.email = email;
        this.teams = teams;
    }
}
