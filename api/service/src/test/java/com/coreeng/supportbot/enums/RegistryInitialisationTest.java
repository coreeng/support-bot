package com.coreeng.supportbot.enums;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.config.EnumProps;
import com.coreeng.supportbot.teams.groups.GroupRef;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class RegistryInitialisationTest {

    @Mock
    private EnumProps enumProps;

    @Mock
    private TagsRepository tagsRepository;

    @Mock
    private ImpactsRepository impactsRepository;

    @Mock
    private EscalationTeamHistoryRepository escalationTeamHistoryRepository;

    @InjectMocks
    private RegistryInitialisation registryInitialisation;

    @Test
    void run_reconcilesEscalationTeams_insertsActiveAndSoftDeletesRemoved() {
        EscalationTeam platform = new EscalationTeam("Platform Team", "platform", new GroupRef.Slack("Splatform"));
        when(enumProps.tags()).thenReturn(ImmutableList.of());
        when(enumProps.impacts()).thenReturn(ImmutableList.of());
        when(enumProps.escalationTeams()).thenReturn(ImmutableList.of(platform));

        registryInitialisation.run(new DefaultApplicationArguments());

        verify(escalationTeamHistoryRepository).insertOrActivate(ImmutableList.of(platform));
        verify(escalationTeamHistoryRepository).deleteAllExcept(ImmutableList.of("platform"));
    }
}
