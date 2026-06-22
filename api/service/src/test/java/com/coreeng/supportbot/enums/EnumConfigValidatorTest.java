package com.coreeng.supportbot.enums;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.config.EnumProps;
import com.coreeng.supportbot.teams.StaticPlatformTeamsProps;
import com.coreeng.supportbot.teams.groups.GroupRef;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class EnumConfigValidatorTest {

    @Mock
    private EnumProps enumProps;

    @Mock
    private StaticPlatformTeamsProps staticTeamsProps;

    @InjectMocks
    private EnumConfigValidator validator;

    @Test
    void run_passes_whenCodesAreUniqueAndNonBlank() {
        when(enumProps.escalationTeams())
                .thenReturn(ImmutableList.of(new EscalationTeam("Platform", "platform", new GroupRef.Slack("SP"))));
        when(enumProps.tags()).thenReturn(ImmutableList.of(new Tag("Networking", "networking")));
        when(enumProps.impacts()).thenReturn(ImmutableList.of(new TicketImpact("Blocking", "blocking")));

        assertThatCode(() -> validator.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
    }

    @Test
    void run_throws_onDuplicateTagCode() {
        when(enumProps.escalationTeams()).thenReturn(ImmutableList.of());
        when(enumProps.tags()).thenReturn(ImmutableList.of(new Tag("First", "dup"), new Tag("Second", "dup")));
        when(enumProps.impacts()).thenReturn(ImmutableList.of());

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate code 'dup'");
    }

    @Test
    void run_throws_onBlankImpactCode() {
        when(enumProps.escalationTeams()).thenReturn(ImmutableList.of());
        when(enumProps.tags()).thenReturn(ImmutableList.of());
        when(enumProps.impacts()).thenReturn(ImmutableList.of(new TicketImpact("No Code", "")));

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("blank code");
    }

    @Test
    void run_throws_onDuplicateStaticTeamCode() {
        when(enumProps.escalationTeams()).thenReturn(ImmutableList.of());
        when(enumProps.tags()).thenReturn(ImmutableList.of());
        when(enumProps.impacts()).thenReturn(ImmutableList.of());
        when(staticTeamsProps.enabled()).thenReturn(true);
        when(staticTeamsProps.teams())
                .thenReturn(List.of(
                        new StaticPlatformTeamsProps.TeamConfig("First", "dup", new GroupRef.Slack("S1")),
                        new StaticPlatformTeamsProps.TeamConfig("Second", "dup", new GroupRef.Slack("S2"))));

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate code 'dup'");
    }
}
