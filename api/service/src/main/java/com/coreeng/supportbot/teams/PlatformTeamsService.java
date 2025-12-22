package com.coreeng.supportbot.teams;

import com.coreeng.supportbot.enums.EscalationTeam;
import com.coreeng.supportbot.enums.EscalationTeamsRegistry;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.stream.Collectors.joining;

@RequiredArgsConstructor
@Slf4j
public class PlatformTeamsService {
    private final PlatformTeamsFetcher teamsFetcher;
    private final PlatformUsersFetcher usersFetcher;
    private final EscalationTeamsRegistry escalationTeamsRegistry;
    private final PlatformTeamsFetchProps fetchProps;

    private final Map<String, PlatformUser> usersByEmail = new HashMap<>();
    private final Map<String, PlatformTeam> teamByName = new HashMap<>();
    private final Map<String, List<PlatformUser>> groupIdToUsers = new HashMap<>();

    @PostConstruct
    void init() {
        Instant start = Instant.now();
        List<PlatformTeamsFetcher.TeamAndGroupTuple> teams = teamsFetcher.fetchTeams();
        validateEscalationTeamsMapping(teams);

        // Build team objects and collect unique group refs
        for (var t : teams) {
            PlatformTeam team = teamByName.computeIfAbsent(t.name(), k -> new PlatformTeam(
                t.name(),
                new HashSet<>(),
                new HashSet<>()
            ));
            team.groupRefs().add(t.groupRef());
        }

        Set<String> uniqueGroupRefs = teams.stream()
            .map(PlatformTeamsFetcher.TeamAndGroupTuple::groupRef)
            .collect(Collectors.toSet());

        int maxConcurrency = Math.max(1, fetchProps.maxConcurrency());
        Duration timeout = fetchProps.timeout();

        Map<String, List<PlatformUsersFetcher.Membership>> membershipsByGroupRef =
            fetchMembershipsInParallel(uniqueGroupRefs, maxConcurrency, timeout);

        // Materialize users and relations on the main thread
        Map<String, List<PlatformUser>> fetchedGroupUsers = new HashMap<>();
        for (Map.Entry<String, List<PlatformUsersFetcher.Membership>> e : membershipsByGroupRef.entrySet()) {
            String groupRef = e.getKey();
            List<PlatformUsersFetcher.Membership> memberships = e.getValue();
            List<PlatformUser> users = new ArrayList<>(memberships.size());
            for (PlatformUsersFetcher.Membership m : memberships) {
                String normalisedEmail = m.email().toLowerCase();
                PlatformUser user = usersByEmail.computeIfAbsent(
                    normalisedEmail, k -> new PlatformUser(
                        normalisedEmail,
                        new HashSet<>()
                    ));
                users.add(user);
            }
            fetchedGroupUsers.put(groupRef, users);
        }

        groupIdToUsers.putAll(fetchedGroupUsers);
        for (PlatformTeam team : teamByName.values()) {
            for (String groupRef : team.groupRefs()) {
                List<PlatformUser> users = groupIdToUsers.getOrDefault(groupRef, List.of());
                team.users().addAll(users);
                for (PlatformUser user : users) {
                    user.teams().add(team);
                }
            }
        }

        log.atInfo()
            .addArgument(teamByName::size)
            .addArgument(groupIdToUsers::size)
            .addArgument(usersByEmail::size)
            .addArgument(() -> Duration.between(start, Instant.now()))
            .log("Finished fetching teams info. Teams({}), Groups({}), Users({}), Elapsed({})");
    }

    private Map<String, List<PlatformUsersFetcher.Membership>> fetchMembershipsInParallel(
        Set<String> groupRefs,
        int maxConcurrency,
        @Nullable Duration timeout
    ) {
        if (groupRefs.isEmpty()) {
            log.warn("No groupRefs to fetch, possibly a configuration issue.");
            return ImmutableMap.of();
        }

        Map<String, CompletableFuture<List<PlatformUsersFetcher.Membership>>> futures = new HashMap<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Semaphore gate = new Semaphore(maxConcurrency);
            for (String groupRef : groupRefs) {
                CompletableFuture<List<PlatformUsersFetcher.Membership>> future =
                    CompletableFuture.supplyAsync(() -> {
                        boolean acquired = false;
                        try {
                            gate.acquire();
                            acquired = true;
                            return usersFetcher.fetchMembershipsByGroupRef(groupRef);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        } catch (Exception e) {
                            log.atWarn()
                                .addKeyValue("groupRef", groupRef)
                                .addKeyValue("error", e)
                                .log("Failed to fetch group members");
                            return ImmutableList.of();
                        } finally {
                            if (acquired) {
                                gate.release();
                            }
                        }
                    }, executor);
                futures.put(groupRef, future);
            }

            CompletableFuture<Void> all = CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0]));
            try {
                if (timeout != null) {
                    all.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
                } else {
                    all.get();
                }
            } catch (ExecutionException ee) {
                futures.values().forEach(f -> f.cancel(true));
                throw new IllegalStateException("Failed to fetch memberships", ee);
            } catch (InterruptedException ie) {
                futures.values().forEach(f -> f.cancel(true));
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while fetching memberships", ie);
            } catch (java.util.concurrent.TimeoutException te) {
                futures.values().forEach(f -> f.cancel(true));
                throw new IllegalStateException("Fetching memberships timed out after " + timeout, te);
            }
        }

        ImmutableMap.Builder<String, List<PlatformUsersFetcher.Membership>> result = ImmutableMap.builder();
        for (Map.Entry<String, CompletableFuture<List<PlatformUsersFetcher.Membership>>> e : futures.entrySet()) {
            result.put(e.getKey(), e.getValue().join());
        }
        return result.build();
    }

    private void validateEscalationTeamsMapping(List<PlatformTeamsFetcher.TeamAndGroupTuple> teams) {
        ImmutableSet<String> teamNames = teams.stream()
            .map(PlatformTeamsFetcher.TeamAndGroupTuple::name)
            .collect(toImmutableSet());
        ImmutableSet<String> escalationTeamNames = escalationTeamsRegistry.listAllEscalationTeams().stream()
            .map(EscalationTeam::code)
            .collect(toImmutableSet());
        var setsDiff = Sets.difference(escalationTeamNames, teamNames);
        if (!setsDiff.isEmpty()) {
            if (fetchProps.ignoreUnknownTeams()) {
                log.info("""
                    Found unknown escalation teams: {}.
                    Ensure that it's expected that these teams are not found among platform teams.""", setsDiff);
            } else {
                throw new IllegalStateException("Unknown escalation teams specified: " +
                                                setsDiff.stream()
                                                    .collect(joining(", ", "[", "]"))
                );
            }
        }
    }

    public ImmutableList<PlatformTeam> listTeams() {
        return ImmutableList.copyOf(teamByName.values());
    }

    public ImmutableList<PlatformTeam> listTeamsByUserEmail(String email) {
        PlatformUser user = usersByEmail.get(email.toLowerCase());
        if (user == null) {
            return ImmutableList.of();
        }
        return ImmutableList.copyOf(user.teams());
    }

    @Nullable
    public PlatformTeam findTeamByName(String name) {
        return teamByName.get(name);
    }

    @Nullable
    public PlatformUser findUserByEmail(String email) {
        return usersByEmail.get(email.toLowerCase());
    }
}
