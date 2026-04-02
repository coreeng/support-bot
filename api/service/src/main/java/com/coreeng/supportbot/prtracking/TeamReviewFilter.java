package com.coreeng.supportbot.prtracking;

import com.coreeng.supportbot.config.PrTrackingProps;
import com.coreeng.supportbot.github.GitHubApiException;
import com.coreeng.supportbot.github.GitHubClient;
import com.coreeng.supportbot.github.GitHubPullRequest;
import com.coreeng.supportbot.github.GitHubPullRequestReview;
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

    private final GitHubClient gitHubClient;

    /**
     * Filters reviews to owning team members. Returns all reviews unfiltered when:
     * - team membership could not be resolved (API failure), or
     * - no team is configured and no teams were requested on the PR.
     */
    public List<GitHubPullRequestReview> filterToOwningTeam(
            List<GitHubPullRequestReview> reviews,
            GitHubPullRequest pr,
            PrTrackingProps.@Nullable Repository repoConfig,
            Map<String, Optional<Set<String>>> cache) {
        Set<String> teamMembers = resolveOwningTeamMembers(pr, repoConfig, cache);
        if (teamMembers == null || teamMembers.isEmpty()) {
            return reviews;
        }
        return reviews.stream().filter(r -> teamMembers.contains(r.userLogin())).toList();
    }

    /** Returns the most recent APPROVED or CHANGES_REQUESTED review, or null if none. */
    public @Nullable GitHubPullRequestReview findLatestActionableReview(List<GitHubPullRequestReview> reviews) {
        return reviews.stream()
                .filter(r -> r.isApproved() || r.requestsChanges())
                .max(Comparator.comparing(GitHubPullRequestReview::submittedAt))
                .orElse(null);
    }

    private @Nullable Set<String> resolveOwningTeamMembers(
            GitHubPullRequest pr,
            PrTrackingProps.@Nullable Repository repoConfig,
            Map<String, Optional<Set<String>>> cache) {
        // Explicit team slug configured — use Teams API
        if (repoConfig != null && repoConfig.githubTeamSlug() != null) {
            String org = Iterables.get(Splitter.on('/').split(pr.repositoryName()), 0);
            return resolveTeamReviewers(org, repoConfig.githubTeamSlug(), cache);
        }
        // No slug — use requested team reviewers already fetched with the PR.
        // If empty (no teams requested), all reviews are accepted without filtering.
        List<String> requestedMembers = pr.requestedTeamReviewerLogins();
        return requestedMembers.isEmpty() ? Set.of() : Set.copyOf(requestedMembers);
    }

    private @Nullable Set<String> resolveTeamReviewers(
            String org, String teamSlug, Map<String, Optional<Set<String>>> cache) {
        String key = org + "/" + teamSlug;
        if (cache.containsKey(key)) {
            // Optional.empty() = previous fetch failed; Optional.of(set) = members resolved
            return cache.get(key).orElse(null);
        }
        try {
            Set<String> members = Set.copyOf(gitHubClient.resolveTeamReviewers(org, teamSlug));
            cache.put(key, Optional.of(members));
            return members;
        } catch (GitHubApiException e) {
            log.atWarn()
                    .addArgument(() -> org + "/" + teamSlug)
                    .addArgument(e::getMessage)
                    .log("Could not fetch team members for {} — skipping team validation: {}");
            cache.put(key, Optional.empty());
            return null;
        }
    }
}
