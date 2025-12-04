package com.coreeng.supportbot.teams.rest;

import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class TeamDto {
    private String name;
    private Set<String> groupRefs;

    public TeamDto(String name, Set<String> groupRefs) {
        this.name = name;
        this.groupRefs = groupRefs;
    }
}
