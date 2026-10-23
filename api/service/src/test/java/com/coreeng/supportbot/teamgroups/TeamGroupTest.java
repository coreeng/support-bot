package com.coreeng.supportbot.teamgroups;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.ImmutableSortedSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TeamGroupTest {

    private static final ImmutableSortedSet<String> TEAMS = ImmutableSortedSet.of("team-a");

    @ParameterizedTest
    @ValueSource(strings = {"", "-area", "area-", "Area", "area a", "área"})
    void rejectsACodeThatIsNotUrlSafe(String code) {
        assertThatThrownBy(() -> new TeamGroup(code, "Area", TEAMS)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsACodeUpToTheMaximumLength_andNoLonger() {
        String longest = "a".repeat(TeamGroup.MAX_CODE_LENGTH);

        assertThat(new TeamGroup(longest, "Area", TEAMS).code()).isEqualTo(longest);
        assertThatThrownBy(() -> new TeamGroup(longest + "a", "Area", TEAMS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsABlankLabel() {
        assertThatThrownBy(() -> new TeamGroup("area-a", " ", TEAMS)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAGroupWithNoTeams() {
        assertThatThrownBy(() -> new TeamGroup("area-a", "Area A", ImmutableSortedSet.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
