package com.coreeng.supportbot.teams.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.teams.TeamDisplay;
import com.coreeng.supportbot.teams.TeamType;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;

class TeamUIMapperTest {

    private final TeamUIMapper mapper = new TeamUIMapper();

    @Test
    void mapsActiveTeamDisplayAsActive() {
        TeamUI ui = mapper.mapToUI(new TeamDisplay("pe", "PE Core", ImmutableList.of(TeamType.TENANT), true));

        assertThat(ui.code()).isEqualTo("pe");
        assertThat(ui.label()).isEqualTo("PE Core");
        assertThat(ui.active()).isTrue();
    }

    @Test
    void mapsRetiredTeamDisplayWithLastKnownLabelAndActiveFalse() {
        TeamUI ui = mapper.mapToUI(new TeamDisplay("old-team", "Old Team", ImmutableList.of(), false));

        assertThat(ui.code()).isEqualTo("old-team");
        assertThat(ui.label()).isEqualTo("Old Team");
        assertThat(ui.active()).isFalse();
    }
}
