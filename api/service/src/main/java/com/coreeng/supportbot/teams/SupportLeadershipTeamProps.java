package com.coreeng.supportbot.teams;

import com.coreeng.supportbot.teams.groups.GroupRef;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("team.leadership")
public record SupportLeadershipTeamProps(String name, String code, GroupRef groupRef) {
    /** Convenience overload that parses {@code groupRef} via {@link GroupRef#parse(String)}. */
    public SupportLeadershipTeamProps(String name, String code, String groupRef) {
        this(name, code, GroupRef.parse(groupRef));
    }

    public String slackId() {
        if (groupRef instanceof GroupRef.Slack slack) {
            return slack.id();
        }
        throw new IllegalStateException(
                "team.leadership.group-ref must reference a Slack group; got " + groupRef.canonical());
    }
}
