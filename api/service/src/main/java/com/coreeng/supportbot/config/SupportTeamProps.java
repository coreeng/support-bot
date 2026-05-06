package com.coreeng.supportbot.config;

import com.coreeng.supportbot.teams.groups.GroupRef;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("team.support")
public record SupportTeamProps(String name, String code, GroupRef groupRef) {
    public SupportTeamProps(String name, String code, String groupRef) {
        this(name, code, GroupRef.parse(groupRef));
    }

    public String slackId() {
        return GroupRef.Slack.idFrom(groupRef, "team.support.group-ref");
    }
}
