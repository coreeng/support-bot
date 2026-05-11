package com.coreeng.supportbot.config;

import com.coreeng.supportbot.teams.groups.GroupRef;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties("team.support")
public record SupportTeamProps(String name, String code, GroupRef groupRef) {
    @ConstructorBinding
    public SupportTeamProps(String name, String code, @Nullable GroupRef groupRef) {
        if (groupRef == null) {
            throw new IllegalStateException("team.support.group-ref is required."
                    + " If you previously used 'slack-group-id', rename it to 'group-ref' (PT-351 migration).");
        }
        this.name = name;
        this.code = code;
        this.groupRef = groupRef;
    }

    public SupportTeamProps(String name, String code, String groupRef) {
        this(name, code, GroupRef.parse(groupRef));
    }

    public String slackId() {
        return GroupRef.Slack.idFrom(groupRef, "team.support.group-ref");
    }
}
