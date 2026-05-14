package com.coreeng.supportbot.enums;

import com.coreeng.supportbot.config.EnumerationValue;
import com.coreeng.supportbot.teams.groups.GroupRef;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

public record EscalationTeam(
        String label,
        String code,
        GroupRef groupRef,
        @Nullable String slackMentionGroupId,
        @Nullable @Deprecated String slackGroupId) implements EnumerationValue {

    private static final Logger LOGGER = LoggerFactory.getLogger(EscalationTeam.class);

    @ConstructorBinding
    public EscalationTeam(
            String label,
            String code,
            @Nullable GroupRef groupRef,
            @Nullable String slackMentionGroupId,
            @Nullable String slackGroupId) {
        if (groupRef == null && slackGroupId != null && !slackGroupId.isBlank()) {
            LOGGER.warn(
                    "'enums.escalation-teams[{}].slack-group-id' is deprecated; use 'group-ref'"
                            + " (e.g. 'slack:{}'). Legacy key will be removed in a future release (PT-351 migration).",
                    code,
                    slackGroupId);
            groupRef = new GroupRef.Slack(slackGroupId);
        } else if (groupRef != null && slackGroupId != null && !slackGroupId.isBlank()) {
            LOGGER.warn(
                    "Both 'group-ref' and deprecated 'slack-group-id' are set on escalation team '{}';"
                            + " 'group-ref' takes precedence. Remove 'slack-group-id' (PT-351 migration).",
                    code);
        }
        if (groupRef == null) {
            throw new IllegalStateException("enums.escalation-teams[" + code + "].group-ref is required."
                    + " If you previously used 'slack-group-id', rename it to 'group-ref' (PT-351 migration).");
        }
        if (!(groupRef instanceof GroupRef.Slack) && (slackMentionGroupId == null || slackMentionGroupId.isBlank())) {
            throw new IllegalStateException("enums.escalation-teams[" + code + "] has non-Slack group-ref "
                    + groupRef.canonical()
                    + " — set 'slack-mention-group-id' to enable Slack mention rendering");
        }
        this.label = label;
        this.code = code;
        this.groupRef = groupRef;
        this.slackMentionGroupId = slackMentionGroupId;
        this.slackGroupId = slackGroupId;
    }

    public EscalationTeam(String label, String code, GroupRef groupRef, @Nullable String slackMentionGroupId) {
        this(label, code, groupRef, slackMentionGroupId, null);
    }

    public EscalationTeam(String label, String code, GroupRef groupRef) {
        this(label, code, groupRef, null, null);
    }

    public EscalationTeam(String label, String code, String groupRef) {
        this(label, code, GroupRef.parse(groupRef), null, null);
    }

    /**
     * Returns the raw Slack subteam ID for {@code <!subteam^...>} mention rendering.
     * The compact constructor guarantees one of the two branches is satisfied.
     */
    public String slackMentionId() {
        if (slackMentionGroupId != null) {
            return slackMentionGroupId;
        }
        return ((GroupRef.Slack) groupRef).id();
    }
}
