package com.coreeng.supportbot.teams;

import com.coreeng.supportbot.enums.EscalationTeam;
import com.coreeng.supportbot.enums.EscalationTeamsRegistry;
import com.coreeng.supportbot.teams.fakes.FakeEscalationTeamsRegistry;
import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TeamServiceTest {

    @Test
    void listTeamsByType_tenant_returnsOnlyPlatformTeams() {
        // given
        PlatformTeamsService platformTeamsService = mock(PlatformTeamsService.class);
        EscalationTeamsRegistry escalationTeamsRegistry = new FakeEscalationTeamsRegistry(List.of(
            new EscalationTeam("Escalation Only 1", "escalation-only-1", "SLACK1"),
            new EscalationTeam("Escalation Only 2", "escalation-only-2", "SLACK2")
        ));
        SupportTeamService supportTeamService = mockSupportTeamService();

        PlatformTeam platformTeam1 = new PlatformTeam("team1", Set.of(), Set.of());
        PlatformTeam platformTeam2 = new PlatformTeam("team2", Set.of(), Set.of());
        when(platformTeamsService.listTeams()).thenReturn(ImmutableList.of(platformTeam1, platformTeam2));

        TeamService teamService = new TeamService(platformTeamsService, escalationTeamsRegistry, supportTeamService);

        // when
        ImmutableList<Team> result = teamService.listTeamsByType(TeamType.tenant);

        // then
        assertEquals(2, result.size());
        assertEquals("team1", result.get(0).code());
        assertEquals(ImmutableList.of(TeamType.tenant), result.get(0).types());
        assertEquals("team2", result.get(1).code());
        assertEquals(ImmutableList.of(TeamType.tenant), result.get(1).types());
    }

    @Test
    void listTeamsByType_escalation_returnsEscalationOnlyTeams() {
        // given
        PlatformTeamsService platformTeamsService = mock(PlatformTeamsService.class);

        PlatformTeam platformOnlyTeam1 = new PlatformTeam("platform-only-1", Set.of(), Set.of());
        PlatformTeam platformOnlyTeam2 = new PlatformTeam("platform-only-2", Set.of(), Set.of());
        when(platformTeamsService.listTeams()).thenReturn(ImmutableList.of(platformOnlyTeam1, platformOnlyTeam2));
        when(platformTeamsService.findTeamByName(anyString())).thenReturn(null);

        EscalationTeamsRegistry escalationTeamsRegistry = new FakeEscalationTeamsRegistry(List.of(
            new EscalationTeam("Escalation Team 1", "esc1", "SLACK1"),
            new EscalationTeam("Escalation Team 2", "esc2", "SLACK2")
        ));
        SupportTeamService supportTeamService = mockSupportTeamService();

        TeamService teamService = new TeamService(platformTeamsService, escalationTeamsRegistry, supportTeamService);

        // when
        ImmutableList<Team> result = teamService.listTeamsByType(TeamType.escalation);

        // then
        assertEquals(2, result.size());
        assertEquals("Escalation Team 1", result.get(0).label());
        assertEquals("esc1", result.get(0).code());
        assertEquals(ImmutableList.of(TeamType.escalation), result.get(0).types());
        assertEquals("Escalation Team 2", result.get(1).label());
        assertEquals("esc2", result.get(1).code());
        assertEquals(ImmutableList.of(TeamType.escalation), result.get(1).types());
    }

    @Test
    void listTeamsByType_escalation_returnsTeamsWithBothTypesWhenAlsoPlatformTeam() {
        // given
        PlatformTeamsService platformTeamsService = mock(PlatformTeamsService.class);

        PlatformTeam platformAndEscalationTeam = new PlatformTeam("esc1", Set.of(), Set.of());
        PlatformTeam platformOnlyTeam = new PlatformTeam("platform-only-noise", Set.of(), Set.of());
        when(platformTeamsService.listTeams()).thenReturn(ImmutableList.of(platformAndEscalationTeam, platformOnlyTeam));
        when(platformTeamsService.findTeamByName("esc1")).thenReturn(platformAndEscalationTeam);
        when(platformTeamsService.findTeamByName("esc2")).thenReturn(null);
        when(platformTeamsService.findTeamByName("platform-only-noise")).thenReturn(platformOnlyTeam);

        EscalationTeamsRegistry escalationTeamsRegistry = new FakeEscalationTeamsRegistry(List.of(
            new EscalationTeam("Escalation Team 1", "esc1", "SLACK1"),
            new EscalationTeam("Escalation Team 2", "esc2", "SLACK2")
        ));
        SupportTeamService supportTeamService = mockSupportTeamService();

        TeamService teamService = new TeamService(platformTeamsService, escalationTeamsRegistry, supportTeamService);

        // when
        ImmutableList<Team> result = teamService.listTeamsByType(TeamType.escalation);

        // then
        assertEquals(2, result.size());

        // First team is both platform and escalation
        assertEquals("Escalation Team 1", result.get(0).label());
        assertEquals("esc1", result.get(0).code());
        assertEquals(ImmutableList.of(TeamType.tenant, TeamType.escalation), result.get(0).types());

        // Second team is escalation only
        assertEquals("Escalation Team 2", result.get(1).label());
        assertEquals("esc2", result.get(1).code());
        assertEquals(ImmutableList.of(TeamType.escalation), result.get(1).types());
    }

    @Test
    void listTeamsByType_support_returnsSupportTeam() {
        // given
        PlatformTeamsService platformTeamsService = mock(PlatformTeamsService.class);
        EscalationTeamsRegistry escalationTeamsRegistry = new FakeEscalationTeamsRegistry(List.of());
        SupportTeamService supportTeamService = mockSupportTeamService();

        TeamService teamService = new TeamService(platformTeamsService, escalationTeamsRegistry, supportTeamService);

        // when
        ImmutableList<Team> result = teamService.listTeamsByType(TeamType.support);

        // then
        assertEquals(1, result.size());
        assertEquals("Support Team", result.get(0).label());
        assertEquals("support", result.get(0).code());
        assertEquals(ImmutableList.of(TeamType.support), result.get(0).types());
    }

    @Test
    void listTeamsByType_leadership_returnsLeadershipTeam() {
        // given
        PlatformTeamsService platformTeamsService = mock(PlatformTeamsService.class);
        EscalationTeamsRegistry escalationTeamsRegistry = new FakeEscalationTeamsRegistry(List.of());
        SupportTeamService supportTeamService = mockSupportTeamService();

        TeamService teamService = new TeamService(platformTeamsService, escalationTeamsRegistry, supportTeamService);

        // when
        ImmutableList<Team> result = teamService.listTeamsByType(TeamType.leadership);

        // then
        assertEquals(1, result.size());
        assertEquals("Leadership Team", result.get(0).label());
        assertEquals("leadership", result.get(0).code());
        assertEquals(ImmutableList.of(TeamType.leadership), result.get(0).types());
    }

    @Test
    void findTeamByCode_returnsSupportTeam() {
        // given
        PlatformTeamsService platformTeamsService = mock(PlatformTeamsService.class);
        EscalationTeamsRegistry escalationTeamsRegistry = new FakeEscalationTeamsRegistry(List.of());
        SupportTeamService supportTeamService = mockSupportTeamService();

        TeamService teamService = new TeamService(platformTeamsService, escalationTeamsRegistry, supportTeamService);

        // when
        Team result = teamService.findTeamByCode("support");

        // then
        assertNotNull(result);
        assertEquals("Support Team", result.label());
        assertEquals("support", result.code());
        assertEquals(ImmutableList.of(TeamType.support), result.types());
    }

    @Test
    void findTeamByCode_returnsPlatformOnlyTeam() {
        // given
        PlatformTeamsService platformTeamsService = mock(PlatformTeamsService.class);
        PlatformTeam platformTeam = new PlatformTeam("platform1", Set.of(), Set.of());
        when(platformTeamsService.findTeamByName("platform1")).thenReturn(platformTeam);
        when(platformTeamsService.findTeamByName("escalation-noise-1")).thenReturn(null);
        when(platformTeamsService.findTeamByName("escalation-noise-2")).thenReturn(null);

        // Add noise: escalation teams with different codes
        EscalationTeamsRegistry escalationTeamsRegistry = new FakeEscalationTeamsRegistry(List.of(
            new EscalationTeam("Escalation Noise 1", "escalation-noise-1", "SLACK1"),
            new EscalationTeam("Escalation Noise 2", "escalation-noise-2", "SLACK2")
        ));
        SupportTeamService supportTeamService = mockSupportTeamService();

        TeamService teamService = new TeamService(platformTeamsService, escalationTeamsRegistry, supportTeamService);

        // when
        Team result = teamService.findTeamByCode("platform1");

        // then
        assertNotNull(result);
        assertEquals("platform1", result.label());
        assertEquals("platform1", result.code());
        assertEquals(ImmutableList.of(TeamType.tenant), result.types());
    }

    @Test
    void findTeamByCode_returnsPlatformTeamWithBothTypesWhenAlsoEscalation() {
        // given
        PlatformTeamsService platformTeamsService = mock(PlatformTeamsService.class);
        PlatformTeam platformTeam = new PlatformTeam("team1", Set.of(), Set.of());
        when(platformTeamsService.findTeamByName("team1")).thenReturn(platformTeam);
        when(platformTeamsService.findTeamByName("other-team")).thenReturn(null);

        EscalationTeamsRegistry escalationTeamsRegistry = new FakeEscalationTeamsRegistry(List.of(
            new EscalationTeam("Team 1", "team1", "SLACK1"),
            new EscalationTeam("Other Team", "other-team", "SLACK2")
        ));
        SupportTeamService supportTeamService = mockSupportTeamService();

        TeamService teamService = new TeamService(platformTeamsService, escalationTeamsRegistry, supportTeamService);

        // when
        Team result = teamService.findTeamByCode("team1");

        // then
        assertNotNull(result);
        assertEquals("Team 1", result.label());
        assertEquals("team1", result.code());
        assertEquals(ImmutableList.of(TeamType.tenant, TeamType.escalation), result.types());
    }

    @Test
    void findTeamByCode_returnsEscalationOnlyTeam() {
        // given
        PlatformTeamsService platformTeamsService = mock(PlatformTeamsService.class);
        when(platformTeamsService.findTeamByName("esc1")).thenReturn(null);
        when(platformTeamsService.findTeamByName("esc2")).thenReturn(null);

        PlatformTeam platformOnlyNoise = new PlatformTeam("platform-noise", Set.of(), Set.of());
        when(platformTeamsService.findTeamByName("platform-noise")).thenReturn(platformOnlyNoise);

        EscalationTeamsRegistry escalationTeamsRegistry = new FakeEscalationTeamsRegistry(List.of(
            new EscalationTeam("Escalation Team 1", "esc1", "SLACK1"),
            new EscalationTeam("Escalation Team 2", "esc2", "SLACK2")
        ));
        SupportTeamService supportTeamService = mockSupportTeamService();

        TeamService teamService = new TeamService(platformTeamsService, escalationTeamsRegistry, supportTeamService);

        // when
        Team result = teamService.findTeamByCode("esc1");

        // then
        assertNotNull(result);
        assertEquals("Escalation Team 1", result.label());
        assertEquals("esc1", result.code());
        assertEquals(ImmutableList.of(TeamType.escalation), result.types());
    }

    @Test
    void findTeamByCode_returnsNullWhenTeamNotFound() {
        // given
        PlatformTeamsService platformTeamsService = mock(PlatformTeamsService.class);
        when(platformTeamsService.findTeamByName("unknown")).thenReturn(null);

        EscalationTeamsRegistry escalationTeamsRegistry = new FakeEscalationTeamsRegistry(List.of());
        SupportTeamService supportTeamService = mockSupportTeamService();

        TeamService teamService = new TeamService(platformTeamsService, escalationTeamsRegistry, supportTeamService);

        // when
        Team result = teamService.findTeamByCode("unknown");

        // then
        assertNull(result);
    }

    @Test
    void listTeams_includesAllTeamTypes() {
        // given
        PlatformTeamsService platformTeamsService = mock(PlatformTeamsService.class);
        PlatformTeam platformOnlyTeam = new PlatformTeam("platform1", Set.of(), Set.of());
        PlatformTeam bothTypesTeam = new PlatformTeam("both1", Set.of(), Set.of());
        when(platformTeamsService.listTeams()).thenReturn(ImmutableList.of(platformOnlyTeam, bothTypesTeam));

        EscalationTeamsRegistry escalationTeamsRegistry = new FakeEscalationTeamsRegistry(List.of(
            new EscalationTeam("Both Team", "both1", "SLACK1")
        ));
        SupportTeamService supportTeamService = mockSupportTeamService();

        TeamService teamService = new TeamService(platformTeamsService, escalationTeamsRegistry, supportTeamService);

        // when
        ImmutableList<Team> result = teamService.listTeams();

        // then
        assertEquals(3, result.size());

        // Verify platform-only team has only tenant type
        Team platformTeam = result.stream().filter(t -> "platform1".equals(t.code())).findFirst().orElse(null);
        assertNotNull(platformTeam);
        assertEquals("platform1", platformTeam.label());
        assertEquals(ImmutableList.of(TeamType.tenant), platformTeam.types());

        // Verify team with both types has both tenant and escalation
        Team bothTeam = result.stream().filter(t -> "both1".equals(t.code())).findFirst().orElse(null);
        assertNotNull(bothTeam);
        assertEquals("Both Team", bothTeam.label());
        assertEquals(ImmutableList.of(TeamType.tenant, TeamType.escalation), bothTeam.types());

        // Verify support team has only support type
        Team supportTeam = result.stream().filter(t -> "support".equals(t.code())).findFirst().orElse(null);
        assertNotNull(supportTeam);
        assertEquals("Support Team", supportTeam.label());
        assertEquals(ImmutableList.of(TeamType.support), supportTeam.types());

        // Verify we have exactly one of each type
        long platformOnlyCount = result.stream().filter(t -> t.types().equals(ImmutableList.of(TeamType.tenant))).count();
        long bothTypesCount = result.stream().filter(t -> t.types().equals(ImmutableList.of(TeamType.tenant, TeamType.escalation))).count();
        long supportOnlyCount = result.stream().filter(t -> t.types().equals(ImmutableList.of(TeamType.support))).count();

        assertEquals(1, platformOnlyCount, "Should have exactly 1 platform-only team");
        assertEquals(1, bothTypesCount, "Should have exactly 1 team with both types");
        assertEquals(1, supportOnlyCount, "Should have exactly 1 support team");
    }

    @Test
    void listTeamsByUserEmail_returnsUserPlatformTeams() {
        // given
        PlatformTeamsService platformTeamsService = mock(PlatformTeamsService.class);
        PlatformTeam userTeam = new PlatformTeam("team1", Set.of(), Set.of());

        PlatformTeam otherUserTeam1 = new PlatformTeam("other-team-1", Set.of(), Set.of());
        PlatformTeam otherUserTeam2 = new PlatformTeam("other-team-2", Set.of(), Set.of());

        when(platformTeamsService.listTeamsByUserEmail("user@test.com")).thenReturn(ImmutableList.of(userTeam));
        when(platformTeamsService.listTeamsByUserEmail("other@test.com")).thenReturn(ImmutableList.of(otherUserTeam1, otherUserTeam2));

        EscalationTeamsRegistry escalationTeamsRegistry = new FakeEscalationTeamsRegistry(List.of());
        SupportTeamService supportTeamService = mockSupportTeamService();

        TeamService teamService = new TeamService(platformTeamsService, escalationTeamsRegistry, supportTeamService);

        // when
        ImmutableList<Team> result = teamService.listTeamsByUserEmail("user@test.com");

        // then
        assertEquals(1, result.size());
        assertEquals("team1", result.get(0).code());
        assertEquals(ImmutableList.of(TeamType.tenant), result.get(0).types());
    }

    @Test
    void listTeamsByUserEmail_includesSupportTeamForSupportMembers() {
        // given
        PlatformTeamsService platformTeamsService = mock(PlatformTeamsService.class);
        PlatformTeam platformTeam = new PlatformTeam("team1", Set.of(), Set.of());
        when(platformTeamsService.listTeamsByUserEmail("support@test.com")).thenReturn(ImmutableList.of(platformTeam));

        EscalationTeamsRegistry escalationTeamsRegistry = new FakeEscalationTeamsRegistry(List.of());
        SupportTeamService supportTeamService = mockSupportTeamService();
        when(supportTeamService.isMemberByUserEmail("support@test.com")).thenReturn(true);

        TeamService teamService = new TeamService(platformTeamsService, escalationTeamsRegistry, supportTeamService);

        // when
        ImmutableList<Team> result = teamService.listTeamsByUserEmail("support@test.com");

        // then
        assertEquals(2, result.size());
        assertEquals("team1", result.get(0).code());
        assertEquals("support", result.get(1).code());
    }

    @Test
    void listTeamsByUserEmail_includesLeadershipTeamForLeadershipMembers() {
        // given
        PlatformTeamsService platformTeamsService = mock(PlatformTeamsService.class);
        PlatformTeam platformTeam = new PlatformTeam("team1", Set.of(), Set.of());
        when(platformTeamsService.listTeamsByUserEmail("leader@test.com")).thenReturn(ImmutableList.of(platformTeam));

        EscalationTeamsRegistry escalationTeamsRegistry = new FakeEscalationTeamsRegistry(List.of());
        SupportTeamService supportTeamService = mockSupportTeamService();
        when(supportTeamService.isLeadershipMemberByUserEmail("leader@test.com")).thenReturn(true);

        TeamService teamService = new TeamService(platformTeamsService, escalationTeamsRegistry, supportTeamService);

        // when
        ImmutableList<Team> result = teamService.listTeamsByUserEmail("leader@test.com");

        // then
        assertEquals(2, result.size());
        assertEquals("team1", result.get(0).code());
        assertEquals("leadership", result.get(1).code());
    }

    @Test
    void listTeamsByUserEmail_includesBothSupportAndLeadershipWhenMemberOfBoth() {
        // given
        PlatformTeamsService platformTeamsService = mock(PlatformTeamsService.class);
        PlatformTeam platformTeam = new PlatformTeam("team1", Set.of(), Set.of());
        when(platformTeamsService.listTeamsByUserEmail("both@test.com")).thenReturn(ImmutableList.of(platformTeam));

        EscalationTeamsRegistry escalationTeamsRegistry = new FakeEscalationTeamsRegistry(List.of());
        SupportTeamService supportTeamService = mockSupportTeamService();
        when(supportTeamService.isMemberByUserEmail("both@test.com")).thenReturn(true);
        when(supportTeamService.isLeadershipMemberByUserEmail("both@test.com")).thenReturn(true);

        TeamService teamService = new TeamService(platformTeamsService, escalationTeamsRegistry, supportTeamService);

        // when
        ImmutableList<Team> result = teamService.listTeamsByUserEmail("both@test.com");

        // then
        assertEquals(3, result.size());
        assertEquals("team1", result.get(0).code());
        assertTrue(result.stream().anyMatch(t -> "support".equals(t.code())));
        assertTrue(result.stream().anyMatch(t -> "leadership".equals(t.code())));
    }

    @Test
    void consistency_teamHasSameTypesRegardlessOfHowItsQueried() {
        // given - team that is both platform and escalation
        PlatformTeamsService platformTeamsService = mock(PlatformTeamsService.class);
        PlatformTeam platformTeam = new PlatformTeam("team1", Set.of(), Set.of());
        when(platformTeamsService.listTeams()).thenReturn(ImmutableList.of(platformTeam));
        when(platformTeamsService.findTeamByName("team1")).thenReturn(platformTeam);

        EscalationTeamsRegistry escalationTeamsRegistry = new FakeEscalationTeamsRegistry(List.of(
            new EscalationTeam("Team 1", "team1", "SLACK1")
        ));
        SupportTeamService supportTeamService = mockSupportTeamService();

        TeamService teamService = new TeamService(platformTeamsService, escalationTeamsRegistry, supportTeamService);

        // when - query the same team in different ways
        Team fromFindByCode = teamService.findTeamByCode("team1");
        Team fromListByTypeTenant = teamService.listTeamsByType(TeamType.tenant).stream()
            .filter(t -> "team1".equals(t.code()))
            .findFirst()
            .orElse(null);
        Team fromListByTypeEscalation = teamService.listTeamsByType(TeamType.escalation).stream()
            .filter(t -> "team1".equals(t.code()))
            .findFirst()
            .orElse(null);
        Team fromListTeams = teamService.listTeams().stream()
            .filter(t -> "team1".equals(t.code()))
            .findFirst()
            .orElse(null);

        // then - all should have the same types
        ImmutableList<TeamType> expectedTypes = ImmutableList.of(TeamType.tenant, TeamType.escalation);

        assertNotNull(fromFindByCode);
        assertEquals(expectedTypes, fromFindByCode.types());

        assertNotNull(fromListByTypeTenant);
        assertEquals(expectedTypes, fromListByTypeTenant.types());

        assertNotNull(fromListByTypeEscalation);
        assertEquals(expectedTypes, fromListByTypeEscalation.types());

        assertNotNull(fromListTeams);
        assertEquals(expectedTypes, fromListTeams.types());
    }

    @Test
    void consistency_escalationOnlyTeamHasOnlyescalationType() {
        // given - escalation team that is NOT a platform team
        PlatformTeamsService platformTeamsService = mock(PlatformTeamsService.class);
        when(platformTeamsService.listTeams()).thenReturn(ImmutableList.of());
        when(platformTeamsService.findTeamByName("esc1")).thenReturn(null);

        EscalationTeamsRegistry escalationTeamsRegistry = new FakeEscalationTeamsRegistry(List.of(
            new EscalationTeam("Escalation Only", "esc1", "SLACK1")
        ));
        SupportTeamService supportTeamService = mockSupportTeamService();

        TeamService teamService = new TeamService(platformTeamsService, escalationTeamsRegistry, supportTeamService);

        // when - query the same team in different ways
        Team fromFindByCode = teamService.findTeamByCode("esc1");
        Team fromListByTypeEscalation = teamService.listTeamsByType(TeamType.escalation).stream()
            .filter(t -> "esc1".equals(t.code()))
            .findFirst()
            .orElse(null);

        // then - both should have only escalation type
        ImmutableList<TeamType> expectedTypes = ImmutableList.of(TeamType.escalation);

        assertNotNull(fromFindByCode);
        assertEquals(expectedTypes, fromFindByCode.types());

        assertNotNull(fromListByTypeEscalation);
        assertEquals(expectedTypes, fromListByTypeEscalation.types());

        // Should NOT appear in tenant list
        ImmutableList<Team> tenantTeams = teamService.listTeamsByType(TeamType.tenant);
        assertTrue(tenantTeams.stream().noneMatch(t -> "esc1".equals(t.code())));
    }

    private SupportTeamService mockSupportTeamService() {
        SupportTeamService supportTeamService = mock(SupportTeamService.class);
        Team supportTeam = new Team("Support Team", "support", ImmutableList.of(TeamType.support));
        Team leadershipTeam = new Team("Leadership Team", "leadership", ImmutableList.of(TeamType.leadership));
        when(supportTeamService.getTeam()).thenReturn(supportTeam);
        when(supportTeamService.getLeadershipTeam()).thenReturn(leadershipTeam);
        when(supportTeamService.isMemberByUserEmail(anyString())).thenReturn(false);
        when(supportTeamService.isLeadershipMemberByUserEmail(anyString())).thenReturn(false);
        return supportTeamService;
    }
}
