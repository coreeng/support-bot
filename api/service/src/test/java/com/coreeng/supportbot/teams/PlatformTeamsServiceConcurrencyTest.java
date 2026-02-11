package com.coreeng.supportbot.teams;

import static org.junit.jupiter.api.Assertions.*;

import com.coreeng.supportbot.enums.EscalationTeam;
import com.coreeng.supportbot.enums.EscalationTeamsRegistry;
import com.coreeng.supportbot.teams.fakes.FakeEscalationTeamsRegistry;
import com.coreeng.supportbot.teams.fakes.FakeTeamsFetcher;
import com.coreeng.supportbot.teams.fakes.SlowUsersFetcher;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class PlatformTeamsServiceConcurrencyTest {
    @Test
    void respectsMaxConcurrencyAndRunsInParallel() {
        // Arrange: 6 groups, each taking ~200ms. With maxConcurrency=2, expect ~3 waves => ~600ms total (+ overhead).
        int groups = 6;
        Duration perCallDelay = Duration.ofMillis(200);
        int maxConcurrency = 2;
        Duration timeout = Duration.ofSeconds(5);

        long serialMillis = perCallDelay.multipliedBy(groups).toMillis();
        long expectedWaves = (long) Math.ceil(groups / (double) maxConcurrency);
        long idealParallelMillis = perCallDelay.multipliedBy(expectedWaves).toMillis();

        List<PlatformTeamsFetcher.TeamAndGroupTuple> teams = new ArrayList<>();
        List<EscalationTeam> escalationTeams = new ArrayList<>();
        for (int i = 1; i <= groups; i++) {
            String teamName = "team-" + i;
            String groupRef = "G" + i;
            teams.add(new PlatformTeamsFetcher.TeamAndGroupTuple(teamName, groupRef));
            escalationTeams.add(new EscalationTeam(teamName, teamName, "SOME"));
        }

        PlatformTeamsFetcher teamsFetcher = new FakeTeamsFetcher(teams);
        SlowUsersFetcher usersFetcher = new SlowUsersFetcher(
                perCallDelay,
                groupRef ->
                        List.of(new PlatformUsersFetcher.Membership(groupRef.toLowerCase(Locale.ROOT) + "@test.com")));

        EscalationTeamsRegistry registry = new FakeEscalationTeamsRegistry(escalationTeams);

        PlatformTeamsFetchProps props = new PlatformTeamsFetchProps(maxConcurrency, timeout, false);
        PlatformTeamsService service = new PlatformTeamsService(teamsFetcher, usersFetcher, registry, props);

        // Act
        Instant start = Instant.now();
        service.init();
        Duration elapsed = Duration.between(start, Instant.now());
        long elapsedMillis = elapsed.toMillis();

        // Assert: concurrency never exceeded the configured gate
        int maxObserved = usersFetcher.maxObservedAtSameTime();
        assertTrue(
                maxObserved <= maxConcurrency,
                "Observed concurrency (" + maxObserved + ") should be <= maxConcurrency (" + maxConcurrency + ")");

        // allow for overhead/jitter: should be < serial and not wildly larger than the ideal parallel time
        assertTrue(
                elapsedMillis < serialMillis,
                "Elapsed (" + elapsedMillis + "ms) should be less than serial time (" + serialMillis + "ms)");
        assertTrue(
                elapsedMillis <= idealParallelMillis + 100,
                "Elapsed (" + elapsedMillis + "ms) should be around ideal parallel time (" + idealParallelMillis
                        + "ms) with tolerance");

        // Sanity: structures are built correctly
        assertEquals(groups, service.listTeams().size());
        for (int i = 1; i <= groups; i++) {
            String teamName = "team-" + i;
            String groupRef = "G" + i;
            var team = service.findTeamByName(teamName);
            assertNotNull(team);
            assertTrue(team.groupRefs().contains(groupRef));
            assertEquals(1, team.users().size());
            var user = service.findUserByEmail(groupRef.toLowerCase(Locale.ROOT) + "@test.com");
            assertNotNull(user);
            assertTrue(service.listTeamsByUserEmail(groupRef.toLowerCase(Locale.ROOT) + "@test.com").stream()
                    .anyMatch(t -> t.name().equals(teamName)));
        }
    }
}
