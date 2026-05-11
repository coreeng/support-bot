package com.coreeng.supportbot.enums;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coreeng.supportbot.teams.groups.GroupRef;
import org.junit.jupiter.api.Test;

class EscalationTeamTest {

    @Test
    void slackMentionId_returnsSlackIdWhenGroupRefIsSlack() {
        var team = new EscalationTeam("WoW", "wow", new GroupRef.Slack("S08948NBMED"));
        assertThat(team.slackMentionId()).isEqualTo("S08948NBMED");
    }

    @Test
    void slackMentionId_returnsExplicitOverride_whenGroupRefIsNonSlack() {
        var team = new EscalationTeam("WoW", "wow", new GroupRef.Google("eng@corp.com"), "S0OVERRIDE");
        assertThat(team.slackMentionId()).isEqualTo("S0OVERRIDE");
    }

    @Test
    void slackMentionId_explicitOverrideTakesPrecedence_evenOverSlackGroupRef() {
        var team = new EscalationTeam("WoW", "wow", new GroupRef.Slack("S08948NBMED"), "S0OVERRIDE");
        assertThat(team.slackMentionId()).isEqualTo("S0OVERRIDE");
    }

    @Test
    void construction_failsFast_whenGroupRefIsNonSlackAndNoOverrideSet() {
        assertThatThrownBy(() -> new EscalationTeam("WoW", "wow", new GroupRef.Google("eng@corp.com")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("wow")
                .hasMessageContaining("google:eng@corp.com")
                .hasMessageContaining("slack-mention-group-id");
    }

    @Test
    void construction_failsFast_whenGroupRefIsNonSlackAndOverrideIsBlank() {
        assertThatThrownBy(() -> new EscalationTeam("WoW", "wow", new GroupRef.Google("eng@corp.com"), "  "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("slack-mention-group-id");
    }
}
