package com.coreeng.supportbot.enums;

import com.coreeng.supportbot.config.EnumerationValue;
import com.coreeng.supportbot.teams.groups.GroupRef;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

public record EscalationTeam(
        String label,
        String code,
        GroupRef groupRef,
        @Nullable String slackMentionGroupId) implements EnumerationValue {

    @ConstructorBinding
    public EscalationTeam(
            String label, String code, @Nullable GroupRef groupRef, @Nullable String slackMentionGroupId) {
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
    }

    public EscalationTeam(String label, String code, GroupRef groupRef) {
        this(label, code, groupRef, null);
    }

    public EscalationTeam(String label, String code, String groupRef) {
        this(label, code, GroupRef.parse(groupRef), null);
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
