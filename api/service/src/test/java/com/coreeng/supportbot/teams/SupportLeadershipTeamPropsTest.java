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
}
