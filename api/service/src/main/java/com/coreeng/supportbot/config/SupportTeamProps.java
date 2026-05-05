package com.coreeng.supportbot.config;

import com.coreeng.supportbot.teams.groups.GroupRef;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("team.support")
public record SupportTeamProps(String name, String code, GroupRef groupRef) {
    /** Convenience overload that parses {@code groupRef} via {@link GroupRef#parse(String)}. */
    public SupportTeamProps(String name, String code, String groupRef) {
        this(name, code, GroupRef.parse(groupRef));
    }

    public String slackId() {
        if (groupRef instanceof GroupRef.Slack slack) {
            return slack.id();
        }
        throw new IllegalStateException(
                "team.support.group-ref must reference a Slack group; got " + groupRef.canonical());
    }
}
