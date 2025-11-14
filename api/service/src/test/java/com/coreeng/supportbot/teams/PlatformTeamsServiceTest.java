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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlatformTeamsServiceTest {
    @Test
    void singleTeamSingleGroup_buildsGraph_andNormalizesEmails() {
        PlatformTeamsFetcher teamsFetcher = new FakeTeamsFetcher(List.of(
            new PlatformTeamsFetcher.TeamAndGroupTuple("wow", "wow-group")
        ));
        PlatformUsersFetcher usersFetcher = new FakeUsersFetcher(
            Map.of("wow-group", List.of(new PlatformUsersFetcher.Membership("WOW1@TEST.COM")))
        );

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
        FakeUsersFetcher usersFetcher = new FakeUsersFetcher(
            Map.of("shared", List.of(
                new PlatformUsersFetcher.Membership("u1@test.com"),
                new PlatformUsersFetcher.Membership("u2@test.com")
            ))
        );

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
        PlatformUsersFetcher usersFetcher = new FakeUsersFetcher(
            Map.of(
                "G1", List.of(new PlatformUsersFetcher.Membership("a@test.com")),
                "G2", List.of(new PlatformUsersFetcher.Membership("b@test.com"))
            )
        );

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
        PlatformUsersFetcher usersFetcher = new FakeUsersFetcher(Map.of());

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
        PlatformUsersFetcher usersFetcher = new FakeUsersFetcher(
            Map.of("G", List.of(new PlatformUsersFetcher.Membership("user@test.com")))
        );

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
        assertTrue(ex.getMessage().toLowerCase().contains("timed out"));
    }

    @Test
    void noTeams_resultsInEmptyState_andNoUserFetchCalls() {
        PlatformTeamsFetcher teamsFetcher = new FakeTeamsFetcher(List.of());
        FakeUsersFetcher usersFetcher = new FakeUsersFetcher(Map.of());
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
        FakeUsersFetcher usersFetcher = new FakeUsersFetcher(
            Map.of("G", List.of(new PlatformUsersFetcher.Membership("u@test.com")))
        );

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
}
