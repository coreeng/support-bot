package com.coreeng.supportbot.teams;

import com.coreeng.supportbot.teams.groups.GroupRef;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

@ConfigurationProperties("team.leadership")
public record SupportLeadershipTeamProps(
        String name,
        String code,
        GroupRef groupRef,
        @Nullable @Deprecated String slackGroupId) {

    private static final Logger LOGGER = LoggerFactory.getLogger(SupportLeadershipTeamProps.class);

    @ConstructorBinding
    public SupportLeadershipTeamProps(
            String name, String code, @Nullable GroupRef groupRef, @Nullable String slackGroupId) {
        if (groupRef == null && slackGroupId != null && !slackGroupId.isBlank()) {
            LOGGER.warn(
                    "'team.leadership.slack-group-id' is deprecated; use 'team.leadership.group-ref'"
                            + " (e.g. 'slack:{}'). Legacy key will be removed in a future release (PT-351 migration).",
                    slackGroupId);
            groupRef = new GroupRef.Slack(slackGroupId);
        } else if (groupRef != null && slackGroupId != null && !slackGroupId.isBlank()) {
            LOGGER.warn("Both 'team.leadership.group-ref' and deprecated 'team.leadership.slack-group-id' are set;"
                    + " 'group-ref' takes precedence. Remove 'slack-group-id' (PT-351 migration).");
        }
        if (groupRef == null) {
            throw new IllegalStateException("team.leadership.group-ref is required."
                    + " If you previously used 'slack-group-id', rename it to 'group-ref' (PT-351 migration).");
        }
        this.name = name;
        this.code = code;
        this.groupRef = groupRef;
        this.slackGroupId = slackGroupId;
    }

    public SupportLeadershipTeamProps(String name, String code, GroupRef groupRef) {
        this(name, code, groupRef, null);
    }

    public SupportLeadershipTeamProps(String name, String code, String groupRef) {
        this(name, code, GroupRef.parse(groupRef), null);
    }

    public String slackId() {
        return GroupRef.Slack.idFrom(groupRef, "team.leadership.group-ref");
    }
}
