package com.coreeng.supportbot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class TeamGroupsPropsTest {

    private static TeamGroupsProps bind(Map<String, String> properties) {
        return new Binder(new MapConfigurationPropertySource(properties))
                .bindOrCreate("team-groups", TeamGroupsProps.class);
    }

    @Test
    void isOffByDefault_withTeamGroupAsTheDisplayName() {
        TeamGroupsProps props = bind(Map.of());

        assertThat(props.attribute()).isNull();
        assertThat(props.displayName()).isEqualTo("Team group");
    }

    @Test
    void bindsTheAttributeAndDisplayName() {
        TeamGroupsProps props = bind(Map.of("team-groups.attribute", "area", "team-groups.display-name", "Domain"));

        assertThat(props.attribute()).isEqualTo("area");
        assertThat(props.displayName()).isEqualTo("Domain");
    }

    @Test
    void rejectsABlankDisplayName() {
        assertThatThrownBy(() -> new TeamGroupsProps(" ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("display-name");
    }

    @Test
    void rejectsABlankAttribute() {
        assertThatThrownBy(() -> new TeamGroupsProps("Area", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("team-groups.attribute");
    }

    @Test
    void stripsPaddingFromTheAttribute_soItStillMatches() {
        assertThat(new TeamGroupsProps("Area", " area ").attribute()).isEqualTo("area");
    }
}
