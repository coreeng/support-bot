package com.coreeng.supportbot.teams;

import com.coreeng.supportbot.teams.groups.GroupRef;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("team.leadership")
public record SupportLeadershipTeamProps(String name, String code, GroupRef groupRef) {
    public SupportLeadershipTeamProps(String name, String code, String groupRef) {
        this(name, code, GroupRef.parse(groupRef));
    }

    public String slackId() {
        return GroupRef.requireSlackId(groupRef, "team.leadership.group-ref");
    }
}
