package com.coreeng.supportbot.enums;

import com.coreeng.supportbot.config.EnumerationValue;
import com.coreeng.supportbot.teams.groups.GroupRef;
import org.jspecify.annotations.Nullable;

public record EscalationTeam(
        String label,
        String code,
        GroupRef groupRef,
        @Nullable String slackMentionGroupId) implements EnumerationValue {

    public EscalationTeam {
        if (groupRef == null) {
            throw new IllegalStateException("enums.escalation-teams[" + code + "].group-ref is required."
                    + " If you previously used 'slack-group-id', rename it to 'group-ref' (PT-351 migration).");
        }
    }

    public EscalationTeam(String label, String code, GroupRef groupRef) {
        this(label, code, groupRef, null);
    }

    public EscalationTeam(String label, String code, String groupRef) {
        this(label, code, GroupRef.parse(groupRef), null);
    }

    /**
     * Returns the raw Slack subteam ID for {@code <!subteam^...>} mention rendering.
     * Falls back to the {@link GroupRef.Slack} id when {@code slackMentionGroupId} is unset.
     * Throws when the team has a non-Slack {@code groupRef} and no explicit mention id is configured.
     */
    public String slackMentionId() {
        if (slackMentionGroupId != null) {
            return slackMentionGroupId;
        }
        if (groupRef instanceof GroupRef.Slack slack) {
            return slack.id();
        }
        throw new IllegalStateException("EscalationTeam '" + code + "' has non-Slack groupRef " + groupRef.canonical()
                + " — set 'slack-mention-group-id' to enable Slack mentions");
    }
}
