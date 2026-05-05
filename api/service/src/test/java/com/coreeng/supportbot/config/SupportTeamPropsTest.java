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
}
