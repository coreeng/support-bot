package com.coreeng.supportbot.teams;

import com.coreeng.supportbot.enums.EscalationTeam;
import com.coreeng.supportbot.enums.EscalationTeamsRegistry;
import com.coreeng.supportbot.teams.fakes.FakeEscalationTeamsRegistry;
import com.coreeng.supportbot.teams.fakes.FakeTeamsFetcher;
import com.coreeng.supportbot.teams.fakes.FakeUsersFetcher;
import com.coreeng.supportbot.teams.fakes.SlowUsersFetcher;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class PlatformTeamsServiceTest {
    @Test
    void singleTeamSingleGroup_buildsGraph_andNormalizesEmails() {
        PlatformTeamsFetcher teamsFetcher = new FakeTeamsFetcher(List.of(
            new PlatformTeamsFetcher.TeamAndGroupTuple("wow", "wow-group")
        ));
        PlatformUsersFetcher usersFetcher = FakeUsersFetcher.builder()
            .memberships("wow-group", List.of(new PlatformUsersFetcher.Membership("WOW1@TEST.COM")))
            .build();

        EscalationTeamsRegistry registry = new FakeEscalationTeamsRegistry(List.of(
            new EscalationTeam("wow", "wow", "SOME")
        ));

        PlatformTeamsFetchProps props = new PlatformTeamsFetchProps(4, Duration.ofSeconds(2), false);
        PlatformTeamsService service = new PlatformTeamsService(teamsFetcher, usersFetcher, registry, props);

        service.init();

        assertEquals(1, service.listTeams().size());
        var team = service.findTeamByName("wow");
        assertNotNull(team);
        assertTrue(team.groupRefs().contains("wow-group"));
        assertEquals(1, team.users().size());

        // normalized lookup
        assertNotNull(service.findUserByEmail("wow1@test.com"));
        assertNotNull(service.findUserByEmail("WOW1@TEST.COM"));
        assertEquals(1, service.listTeamsByUserEmail("wow1@test.com").size());
        assertEquals("wow", service.listTeamsByUserEmail("wow1@test.com").getFirst().name());
    }

    @Test
    void twoTeamsSharingOneGroup_dedupsFetch_andLinksUsersToBothTeams() {
        PlatformTeamsFetcher teamsFetcher = new FakeTeamsFetcher(List.of(
            new PlatformTeamsFetcher.TeamAndGroupTuple("A", "shared"),
            new PlatformTeamsFetcher.TeamAndGroupTuple("B", "shared")
        ));
        FakeUsersFetcher usersFetcher = FakeUsersFetcher.builder()
            .memberships("shared", List.of(
                new PlatformUsersFetcher.Membership("u1@test.com"),
                new PlatformUsersFetcher.Membership("u2@test.com")
            ))
            .build();

        EscalationTeamsRegistry registry = new FakeEscalationTeamsRegistry(List.of(
            new EscalationTeam("A", "A", "SOME"),
            new EscalationTeam("B", "B", "SOME")
        ));

        PlatformTeamsFetchProps props = new PlatformTeamsFetchProps(4, Duration.ofSeconds(2), false);
        PlatformTeamsService service = new PlatformTeamsService(teamsFetcher, usersFetcher, registry, props);

        service.init();

        assertEquals(1, usersFetcher.getCallCount("shared"));

        var teamA = service.findTeamByName("A");
        var teamB = service.findTeamByName("B");
        assertNotNull(teamA);
        assertNotNull(teamB);
        assertEquals(2, teamA.users().size());
        assertEquals(2, teamB.users().size());

        assertEquals(2, service.listTeamsByUserEmail("u1@test.com").size());
        assertEquals(2, service.listTeamsByUserEmail("u2@test.com").size());
    }

    @Test
    void singleTeamTwoGroups_unionsUsers() {
        PlatformTeamsFetcher teamsFetcher = new FakeTeamsFetcher(List.of(
            new PlatformTeamsFetcher.TeamAndGroupTuple("T1", "G1"),
            new PlatformTeamsFetcher.TeamAndGroupTuple("T1", "G2")
        ));
        PlatformUsersFetcher usersFetcher = FakeUsersFetcher.builder()
            .memberships("G1", List.of(new PlatformUsersFetcher.Membership("a@test.com")))
            .memberships("G2", List.of(new PlatformUsersFetcher.Membership("b@test.com")))
            .build();

        EscalationTeamsRegistry registry = new FakeEscalationTeamsRegistry(List.of(
            new EscalationTeam("T1", "T1", "SOME")
        ));

        PlatformTeamsFetchProps props = new PlatformTeamsFetchProps(4, Duration.ofSeconds(2), false);
        PlatformTeamsService service = new PlatformTeamsService(teamsFetcher, usersFetcher, registry, props);

        service.init();

        var t1 = service.findTeamByName("T1");
        assertNotNull(t1);
        assertEquals(2, t1.users().size());
        assertEquals(1, service.listTeamsByUserEmail("a@test.com").size());
        assertEquals(1, service.listTeamsByUserEmail("b@test.com").size());
    }

    @Test
    void escalationMappingMismatch_failsInit() {
        PlatformTeamsFetcher teamsFetcher = new FakeTeamsFetcher(List.of(
            new PlatformTeamsFetcher.TeamAndGroupTuple("wow", "G")
        ));
        PlatformUsersFetcher usersFetcher = FakeUsersFetcher.builder().build();

        EscalationTeamsRegistry registry = new FakeEscalationTeamsRegistry(List.of(
            new EscalationTeam("wow", "wow", "SOME"),
            new EscalationTeam("unknown", "unknown", "SOME")
        ));

        PlatformTeamsFetchProps props = new PlatformTeamsFetchProps(2, Duration.ofSeconds(1), false);
        PlatformTeamsService service = new PlatformTeamsService(teamsFetcher, usersFetcher, registry, props);

        IllegalStateException ex = assertThrows(IllegalStateException.class, service::init);
        assertEquals("Unknown escalation teams specified: [unknown]", ex.getMessage());
    }

    @Test
    void escalationMappingMismatch_ignoresUnknownTeamsWhenFlagIsTrue() {
        PlatformTeamsFetcher teamsFetcher = new FakeTeamsFetcher(List.of(
            new PlatformTeamsFetcher.TeamAndGroupTuple("wow", "G")
        ));
        PlatformUsersFetcher usersFetcher = FakeUsersFetcher.builder()
            .memberships("G", List.of(new PlatformUsersFetcher.Membership("user@test.com")))
            .build();

        EscalationTeamsRegistry registry = new FakeEscalationTeamsRegistry(List.of(
            new EscalationTeam("wow", "wow", "SOME"),
            new EscalationTeam("unknown", "unknown", "SOME"),
            new EscalationTeam("another-unknown", "another-unknown", "SOME2")
        ));

        PlatformTeamsFetchProps props = new PlatformTeamsFetchProps(2, Duration.ofSeconds(1), true);
        PlatformTeamsService service = new PlatformTeamsService(teamsFetcher, usersFetcher, registry, props);

        // Should not throw an exception
        service.init();

        // Verify that the service initialized correctly with the known team
        assertEquals(1, service.listTeams().size());
        var team = service.findTeamByName("wow");
        assertNotNull(team);
        assertEquals("wow", team.name());
        assertEquals(1, team.users().size());
    }

    @Test
    void globalTimeout_triggersFailure() {
        PlatformTeamsFetcher teamsFetcher = new FakeTeamsFetcher(List.of(
            new PlatformTeamsFetcher.TeamAndGroupTuple("T", "slow-G")
        ));
        PlatformUsersFetcher usersFetcher = new SlowUsersFetcher(Duration.ofMillis(200));
        EscalationTeamsRegistry registry = new FakeEscalationTeamsRegistry(List.of(
            new EscalationTeam("T", "T", "SOME")
        ));

        PlatformTeamsFetchProps props = new PlatformTeamsFetchProps(1, Duration.ofMillis(50), false);
        PlatformTeamsService service = new PlatformTeamsService(teamsFetcher, usersFetcher, registry, props);

        IllegalStateException ex = assertThrows(IllegalStateException.class, service::init);
        assertTrue(String.valueOf(ex.getMessage()).toLowerCase(Locale.ROOT).contains("timed out"));
    }

    @Test
    void noTeams_resultsInEmptyState_andNoUserFetchCalls() {
        PlatformTeamsFetcher teamsFetcher = new FakeTeamsFetcher(List.of());
        FakeUsersFetcher usersFetcher = FakeUsersFetcher.builder().build();
        EscalationTeamsRegistry registry = new FakeEscalationTeamsRegistry(List.of());

        PlatformTeamsFetchProps props = new PlatformTeamsFetchProps(2, Duration.ofSeconds(1), false);
        PlatformTeamsService service = new PlatformTeamsService(teamsFetcher, usersFetcher, registry, props);

        service.init();

        assertEquals(0, usersFetcher.getTotalCalls());
        assertTrue(service.listTeams().isEmpty());
    }

    @Test
    void duplicateTuples_sameTeamAndGroup_areIdempotent() {
        PlatformTeamsFetcher teamsFetcher = new FakeTeamsFetcher(List.of(
            new PlatformTeamsFetcher.TeamAndGroupTuple("T", "G"),
            new PlatformTeamsFetcher.TeamAndGroupTuple("T", "G")
        ));
        FakeUsersFetcher usersFetcher = FakeUsersFetcher.builder()
            .memberships("G", List.of(new PlatformUsersFetcher.Membership("u@test.com")))
            .build();

        EscalationTeamsRegistry registry = new FakeEscalationTeamsRegistry(List.of(
            new EscalationTeam("T", "T", "SOME")
        ));

        PlatformTeamsFetchProps props = new PlatformTeamsFetchProps(4, Duration.ofSeconds(2), false);
        PlatformTeamsService service = new PlatformTeamsService(teamsFetcher, usersFetcher, registry, props);

        service.init();

        assertEquals(1, usersFetcher.getCallCount("G"));
        var t = service.findTeamByName("T");
        assertNotNull(t);
        assertEquals(1, t.groupRefs().size());
        assertTrue(t.groupRefs().contains("G"));
        assertEquals(1, t.users().size());
        assertEquals(1, service.listTeamsByUserEmail("u@test.com").size());
    }

    @Test
    void fetchingGroupMembers_failsForOneGroup_serviceInitializesWithPartialData() {
        PlatformTeamsFetcher teamsFetcher = new FakeTeamsFetcher(List.of(
            new PlatformTeamsFetcher.TeamAndGroupTuple("TeamA", "working-group"),
            new PlatformTeamsFetcher.TeamAndGroupTuple("TeamB", "failing-group")
        ));
        FakeUsersFetcher usersFetcher = FakeUsersFetcher.builder()
            .memberships("working-group", List.of(
                new PlatformUsersFetcher.Membership("user1@test.com"),
                new PlatformUsersFetcher.Membership("user2@test.com")
            ))
            .failingGroup("failing-group", new RuntimeException("Simulated API failure"))
            .build();

        EscalationTeamsRegistry registry = new FakeEscalationTeamsRegistry(List.of(
            new EscalationTeam("TeamA", "TeamA", "SOME"),
            new EscalationTeam("TeamB", "TeamB", "SOME")
        ));

        PlatformTeamsFetchProps props = new PlatformTeamsFetchProps(4, Duration.ofSeconds(2), false);
        PlatformTeamsService service = new PlatformTeamsService(teamsFetcher, usersFetcher, registry, props);

        // Should not throw - the catch-all handles the failure gracefully
        service.init();

        // Both teams should exist
        assertEquals(2, service.listTeams().size());

        // TeamA with working group should have its users
        var teamA = service.findTeamByName("TeamA");
        assertNotNull(teamA);
        assertEquals(2, teamA.users().size());

        // TeamB with failing group should have zero users
        var teamB = service.findTeamByName("TeamB");
        assertNotNull(teamB);
        assertEquals(0, teamB.users().size());

        // Users from working group should be queryable
        assertNotNull(service.findUserByEmail("user1@test.com"));
        assertNotNull(service.findUserByEmail("user2@test.com"));
        assertEquals(1, service.listTeamsByUserEmail("user1@test.com").size());
    }

    @Test
    void fetchingGroupMembers_failsForAllGroups_serviceInitializesWithEmptyUsers() {
        PlatformTeamsFetcher teamsFetcher = new FakeTeamsFetcher(List.of(
            new PlatformTeamsFetcher.TeamAndGroupTuple("TeamA", "failing-group-1"),
            new PlatformTeamsFetcher.TeamAndGroupTuple("TeamB", "failing-group-2")
        ));
        FakeUsersFetcher usersFetcher = FakeUsersFetcher.builder()
            .failingGroup("failing-group-1", new RuntimeException("API error 1"))
            .failingGroup("failing-group-2", new IllegalStateException("API error 2"))
            .build();

        EscalationTeamsRegistry registry = new FakeEscalationTeamsRegistry(List.of(
            new EscalationTeam("TeamA", "TeamA", "SOME"),
            new EscalationTeam("TeamB", "TeamB", "SOME")
        ));

        PlatformTeamsFetchProps props = new PlatformTeamsFetchProps(4, Duration.ofSeconds(2), false);
        PlatformTeamsService service = new PlatformTeamsService(teamsFetcher, usersFetcher, registry, props);

        // Should not throw - the catch-all handles all failures gracefully
        service.init();

        // Teams should exist but with no users
        assertEquals(2, service.listTeams().size());

        var teamA = service.findTeamByName("TeamA");
        assertNotNull(teamA);
        assertEquals(0, teamA.users().size());

        var teamB = service.findTeamByName("TeamB");
        assertNotNull(teamB);
        assertEquals(0, teamB.users().size());

        // No users should be found
        assertNull(service.findUserByEmail("any@test.com"));
        assertTrue(service.listTeamsByUserEmail("any@test.com").isEmpty());
    }
}
