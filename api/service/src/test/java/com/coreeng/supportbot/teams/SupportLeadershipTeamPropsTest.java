package com.coreeng.supportbot.teams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coreeng.supportbot.teams.groups.GroupRef;
import org.junit.jupiter.api.Test;

class SupportLeadershipTeamPropsTest {

    @Test
    void slackId_returnsRawIdWhenGroupRefIsSlack() {
        var props = new SupportLeadershipTeamProps("Leadership", "leadership", new GroupRef.Slack("S01234LEADER"));
        assertThat(props.slackId()).isEqualTo("S01234LEADER");
    }

    @Test
    void slackId_throwsWhenGroupRefIsNotSlack() {
        var props = new SupportLeadershipTeamProps("Leadership", "leadership", new GroupRef.Google("leads@corp.com"));
        assertThatThrownBy(props::slackId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must reference a Slack group")
                .hasMessageContaining("google:leads@corp.com");
    }

    @Test
    @SuppressWarnings("NullAway")
    void construction_throwsMigrationHint_whenGroupRefIsNull() {
        GroupRef nullRef = null;
        assertThatThrownBy(() -> new SupportLeadershipTeamProps("Leadership", "leadership", nullRef))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("team.leadership.group-ref is required")
                .hasMessageContaining("slack-group-id")
                .hasMessageContaining("PT-351");
    }

    @Test
    void construction_acceptsLegacySlackGroupId_andPromotesToSlackGroupRef() {
        var props = new SupportLeadershipTeamProps("Leadership", "leadership", null, "S01234LEGACY");
        assertThat(props.groupRef()).isEqualTo(new GroupRef.Slack("S01234LEGACY"));
        assertThat(props.slackId()).isEqualTo("S01234LEGACY");
    }

    @Test
    void construction_prefersGroupRefWhenBothLegacyAndNewKeysSet() {
        var props = new SupportLeadershipTeamProps(
                "Leadership", "leadership", new GroupRef.Slack("S01234LEADER"), "S01234LEGACY");
        assertThat(props.groupRef()).isEqualTo(new GroupRef.Slack("S01234LEADER"));
    }

    @Test
    void construction_treatsBlankLegacySlackGroupIdAsAbsent() {
        assertThatThrownBy(() -> new SupportLeadershipTeamProps("Leadership", "leadership", null, "  "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("team.leadership.group-ref is required");
    }
}
