package com.coreeng.supportbot.teams;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.stream.Collectors.joining;

import com.coreeng.supportbot.enums.EscalationTeam;
import com.coreeng.supportbot.enums.EscalationTeamsRegistry;
import com.coreeng.supportbot.teams.groups.GroupRef;
import com.coreeng.supportbot.teams.groups.GroupResolver;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor
@Slf4j
public class PlatformTeamsService {
    private final PlatformTeamsFetcher teamsFetcher;
    private final GroupResolver groupResolver;
    private final EscalationTeamsRegistry escalationTeamsRegistry;
    private final PlatformTeamsFetchProps fetchProps;

    private final Map<String, PlatformUser> usersByEmail = new HashMap<>();
    private final Map<String, PlatformTeam> teamByCode = new HashMap<>();
    private final Map<GroupRef, List<PlatformUser>> groupRefToUsers = new HashMap<>();

    @PostConstruct
    void init() {
        Instant start = Instant.now();
        List<PlatformTeamsFetcher.TeamAndGroupTuple> teams = teamsFetcher.fetchTeams();
        validateEscalationTeamsMapping(teams);

        // Build team objects and collect unique group refs
        for (var t : teams) {
            PlatformTeam team = teamByCode.computeIfAbsent(
                    t.code(), k -> new PlatformTeam(t.name(), t.code(), new HashSet<>(), new HashSet<>()));
            team.groupRefs().add(t.groupRef());
        }

        Set<GroupRef> uniqueGroupRefs = teams.stream()
                .map(PlatformTeamsFetcher.TeamAndGroupTuple::groupRef)
                .collect(Collectors.toSet());

        int maxConcurrency = Math.max(1, fetchProps.maxConcurrency());
        Duration timeout = fetchProps.timeout();

        Map<GroupRef, List<PlatformUsersFetcher.Membership>> membershipsByGroupRef =
                fetchMembershipsInParallel(uniqueGroupRefs, maxConcurrency, timeout);

        // Materialize users and relations on the main thread
        Map<GroupRef, List<PlatformUser>> fetchedGroupUsers = new HashMap<>();
        for (Map.Entry<GroupRef, List<PlatformUsersFetcher.Membership>> e : membershipsByGroupRef.entrySet()) {
            GroupRef groupRef = e.getKey();
            List<PlatformUsersFetcher.Membership> memberships = e.getValue();
            List<PlatformUser> users = new ArrayList<>(memberships.size());
            for (PlatformUsersFetcher.Membership m : memberships) {
                String normalisedEmail = m.email().toLowerCase(Locale.ROOT);
                PlatformUser user = usersByEmail.computeIfAbsent(
                        normalisedEmail, k -> new PlatformUser(normalisedEmail, new HashSet<>()));
                users.add(user);
            }
            fetchedGroupUsers.put(groupRef, users);
        }

        groupRefToUsers.putAll(fetchedGroupUsers);
        for (PlatformTeam team : teamByCode.values()) {
            for (GroupRef groupRef : team.groupRefs()) {
                List<PlatformUser> users = groupRefToUsers.getOrDefault(groupRef, List.of());
                team.users().addAll(users);
                for (PlatformUser user : users) {
                    user.teams().add(team);
                }
            }
        }

        log.atInfo()
                .addArgument(teamByCode::size)
                .addArgument(groupRefToUsers::size)
                .addArgument(usersByEmail::size)
                .addArgument(() -> Duration.between(start, Instant.now()))
                .log("Finished fetching teams info. Teams({}), Groups({}), Users({}), Elapsed({})");
    }

    private Map<GroupRef, List<PlatformUsersFetcher.Membership>> fetchMembershipsInParallel(
            Set<GroupRef> groupRefs, int maxConcurrency, @Nullable Duration timeout) {
        if (groupRefs.isEmpty()) {
            log.warn("No groupRefs to fetch, possibly a configuration issue.");
            return ImmutableMap.of();
        }

        Map<GroupRef, CompletableFuture<List<PlatformUsersFetcher.Membership>>> futures = new HashMap<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Semaphore gate = new Semaphore(maxConcurrency);
            for (GroupRef groupRef : groupRefs) {
                CompletableFuture<List<PlatformUsersFetcher.Membership>> future = CompletableFuture.supplyAsync(
                        () -> {
                            boolean acquired = false;
                            try {
                                gate.acquire();
                                acquired = true;
                                return groupResolver.resolveMembers(groupRef);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new RuntimeException(e);
                            } catch (Exception e) {
                                log.atWarn()
                                        .setCause(e)
                                        .addKeyValue("groupRef", groupRef.canonical())
                                        .log("Failed to fetch group members");
                                return ImmutableList.of();
                            } finally {
                                if (acquired) {
                                    gate.release();
                                }
                            }
                        },
                        executor);
                futures.put(groupRef, future);
            }

            CompletableFuture<Void> all =
                    CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0]));
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

        ImmutableMap.Builder<GroupRef, List<PlatformUsersFetcher.Membership>> result = ImmutableMap.builder();
        for (Map.Entry<GroupRef, CompletableFuture<List<PlatformUsersFetcher.Membership>>> e : futures.entrySet()) {
            result.put(e.getKey(), e.getValue().join());
        }
        return result.build();
    }

    private void validateEscalationTeamsMapping(List<PlatformTeamsFetcher.TeamAndGroupTuple> teams) {
        ImmutableSet<String> teamCodes =
                teams.stream().map(PlatformTeamsFetcher.TeamAndGroupTuple::code).collect(toImmutableSet());
        ImmutableSet<String> escalationTeamCodes = escalationTeamsRegistry.listAllEscalationTeams().stream()
                .map(EscalationTeam::code)
                .collect(toImmutableSet());
        var setsDiff = Sets.difference(escalationTeamCodes, teamCodes);
        if (!setsDiff.isEmpty()) {
            if (fetchProps.ignoreUnknownTeams()) {
                log.info("""
                    Found unknown escalation teams: {}.
                    Ensure that it's expected that these teams are not found among platform teams.""", setsDiff);
            } else {
                throw new IllegalStateException("Unknown escalation teams specified: "
                        + setsDiff.stream().collect(joining(", ", "[", "]")));
            }
        }
    }

    public ImmutableList<PlatformTeam> listTeams() {
        return ImmutableList.copyOf(teamByCode.values());
    }

    public ImmutableList<PlatformTeam> listTeamsByUserEmail(String email) {
        PlatformUser user = usersByEmail.get(email.toLowerCase(Locale.ROOT));
        if (user == null) {
            return ImmutableList.of();
        }
        return ImmutableList.copyOf(user.teams());
    }

    @Nullable public PlatformTeam findTeamByCode(String code) {
        return teamByCode.get(code);
    }

    @Nullable public PlatformUser findUserByEmail(String email) {
        return usersByEmail.get(email.toLowerCase(Locale.ROOT));
    }
}
