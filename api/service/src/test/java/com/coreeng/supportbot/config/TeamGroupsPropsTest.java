package com.coreeng.supportbot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TeamGroupsPropsTest {

    @Test
    void acceptsGroupsThatShareATeam() {
        // Overlapping areas are real: a shared platform team can belong to more than one.
        TeamGroupsProps props = new TeamGroupsProps(
                "Area",
                List.of(
                        new TeamGroupsProps.Group("area-a", "Area A", List.of("shared", "team-a")),
                        new TeamGroupsProps.Group("area-b", "Area B", List.of("shared", "team-b"))));

        assertThat(props.groups()).hasSize(2);
    }

    @Test
    void rejectsABlankDisplayName() {
        assertThatThrownBy(() -> new TeamGroupsProps(" ", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("display-name");
    }

    @Test
    void rejectsTwoGroupsWithTheSameCode() {
        assertThatThrownBy(() -> new TeamGroupsProps(
                        "Area",
                        List.of(
                                new TeamGroupsProps.Group("area-a", "Area A", List.of("team-a")),
                                new TeamGroupsProps.Group("area-a", "Area A again", List.of("team-b")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("area-a");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Area-A", "area_a", "-area", "area-", "area a", "área"})
    void rejectsACodeThatIsNotUrlSafe(String code) {
        assertThatThrownBy(() -> new TeamGroupsProps.Group(code, "Area", List.of("team-a")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("code");
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "addds", "area-1", "1-2-3"})
    void acceptsAUrlSafeCode(String code) {
        assertThat(new TeamGroupsProps.Group(code, "Area", List.of("team-a")).code())
                .isEqualTo(code);
    }

    @Test
    void rejectsABlankLabel() {
        assertThatThrownBy(() -> new TeamGroupsProps.Group("area-a", "  ", List.of("team-a")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("label");
    }

    @Test
    void rejectsAGroupWithNoTeams() {
        assertThatThrownBy(() -> new TeamGroupsProps.Group("area-a", "Area A", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("team-codes");
    }

    @Test
    void rejectsABlankOrRepeatedTeamCode() {
        assertThatThrownBy(() -> new TeamGroupsProps.Group("area-a", "Area A", List.of("team-a", " ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
        assertThatThrownBy(() -> new TeamGroupsProps.Group("area-a", "Area A", List.of("team-a", "team-a")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("more than once");
    }
}
