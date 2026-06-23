package com.coreeng.supportbot.prtracking;

import com.coreeng.supportbot.config.PrTrackingProps;
import com.coreeng.supportbot.prtracking.source.PrMetadata;
import com.coreeng.supportbot.prtracking.source.PrSourceClients;
import com.coreeng.supportbot.prtracking.source.PrSourceException;
import com.coreeng.supportbot.prtracking.source.Provider;
import com.coreeng.supportbot.prtracking.source.RepoCoord;
import com.coreeng.supportbot.prtracking.source.Review;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "pr-review-tracking.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class TeamReviewFilter {

    private final PrSourceClients prSourceClients;

    /**
     * Filters reviews to owning team members. Returns all reviews unfiltered when:
     * - team membership could not be resolved (API failure), or
     * - no team is configured and no teams were requested on the PR.
     */
    public List<Review> filterToOwningTeam(
            List<Review> reviews,
            PrMetadata pr,
            PrTrackingProps.@Nullable Repository repoConfig,
            Map<String, Optional<Set<String>>> cache) {
        Set<String> teamMembers = resolveOwningTeamMembers(pr, repoConfig, cache);
        if (teamMembers == null || teamMembers.isEmpty()) {
            return reviews;
        }
        return reviews.stream().filter(r -> teamMembers.contains(r.userLogin())).toList();
    }

    /** Returns the most recent APPROVED or CHANGES_REQUESTED review, or null if none. */
    public @Nullable Review findLatestActionableReview(List<Review> reviews) {
        return reviews.stream()
                .filter(r -> r.isApproved() || r.requestsChanges())
                .max(Comparator.comparing(Review::submittedAt))
                .orElse(null);
    }

    private @Nullable Set<String> resolveOwningTeamMembers(
            PrMetadata pr, PrTrackingProps.@Nullable Repository repoConfig, Map<String, Optional<Set<String>>> cache) {
        if (pr.coord().provider() == Provider.GITLAB) {
            // GitLab: only an explicit group path triggers filtering. No equivalent to GitHub's
            // "requested team reviewers" — empty means accept all.
            if (repoConfig != null && repoConfig.gitlabGroupPath() != null) {
                return resolveTeamMembers(pr.coord(), repoConfig.gitlabGroupPath(), cache);
            }
            return Set.of();
        }
        // GitHub: explicit team slug configured — use Teams API
        if (repoConfig != null && repoConfig.githubTeamSlug() != null) {
            return resolveTeamMembers(pr.coord(), repoConfig.githubTeamSlug(), cache);
        }
        // GitHub fallback: requested team reviewers already fetched with the PR. Empty list means
        // no teams were requested, so all reviews are accepted without filtering.
        List<String> requestedMembers = pr.requestedTeamReviewerLogins();
        return requestedMembers.isEmpty() ? Set.of() : Set.copyOf(requestedMembers);
    }

    /**
     * Resolves a team/group reference to its member logins, memoising success or failure in the
     * supplied per-poll cache. Returns {@code null} when membership couldn't be resolved (API failure) —
     * callers treat that as "cannot verify". The cache key folds in the provider so a GitHub team slug
     * and a GitLab group path sharing a name can't collide.
     */
    public @Nullable Set<String> resolveTeamMembers(
            RepoCoord coord, String teamRef, Map<String, Optional<Set<String>>> cache) {
        String scope = coord.provider() == Provider.GITHUB
                ? Iterables.get(Splitter.on('/').split(coord.name()), 0)
                : coord.name();
        String key = coord.provider() + ":" + scope + "/" + teamRef;
        if (cache.containsKey(key)) {
            // Optional.empty() = previous fetch failed; Optional.of(set) = members resolved
            return cache.get(key).orElse(null);
        }
        try {
            Set<String> members =
                    Set.copyOf(prSourceClients.forProvider(coord.provider()).resolveTeamMembers(coord, teamRef));
            cache.put(key, Optional.of(members));
            return members;
        } catch (PrSourceException e) {
            log.atWarn()
                    .addArgument(() -> key)
                    .addArgument(e::getMessage)
                    .log("Could not fetch team members for {} — skipping team validation: {}");
            cache.put(key, Optional.empty());
            return null;
        }
    }
}
