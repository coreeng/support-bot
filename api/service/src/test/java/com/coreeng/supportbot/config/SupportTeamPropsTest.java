package com.coreeng.supportbot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coreeng.supportbot.teams.groups.GroupRef;
import org.junit.jupiter.api.Test;

class SupportTeamPropsTest {

    @Test
    void slackId_returnsRawIdWhenGroupRefIsSlack() {
        var props = new SupportTeamProps("Support", "support", new GroupRef.Slack("S08948NBMED"));
        assertThat(props.slackId()).isEqualTo("S08948NBMED");
    }

    @Test
    void slackId_throwsWhenGroupRefIsNotSlack() {
        var props = new SupportTeamProps("Support", "support", new GroupRef.Static("wow-group"));
        assertThatThrownBy(props::slackId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must reference a Slack group")
                .hasMessageContaining("static:wow-group");
    }

    @Test
    @SuppressWarnings("NullAway")
    void construction_throwsMigrationHint_whenGroupRefIsNull() {
        GroupRef nullRef = null;
        assertThatThrownBy(() -> new SupportTeamProps("Support", "support", nullRef))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("team.support.group-ref is required")
                .hasMessageContaining("slack-group-id")
                .hasMessageContaining("PT-351");
    }

    @Test
    void construction_acceptsLegacySlackGroupId_andPromotesToSlackGroupRef() {
        var props = new SupportTeamProps("Support", "support", null, "S01234LEGACY");
        assertThat(props.groupRef()).isEqualTo(new GroupRef.Slack("S01234LEGACY"));
        assertThat(props.slackId()).isEqualTo("S01234LEGACY");
    }

    @Test
    void construction_prefersGroupRefWhenBothLegacyAndNewKeysSet() {
        var props = new SupportTeamProps("Support", "support", new GroupRef.Slack("S08948NBMED"), "S01234LEGACY");
        assertThat(props.groupRef()).isEqualTo(new GroupRef.Slack("S08948NBMED"));
    }

    @Test
    void construction_treatsBlankLegacySlackGroupIdAsAbsent() {
        assertThatThrownBy(() -> new SupportTeamProps("Support", "support", null, "  "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("team.support.group-ref is required");
    }
}
