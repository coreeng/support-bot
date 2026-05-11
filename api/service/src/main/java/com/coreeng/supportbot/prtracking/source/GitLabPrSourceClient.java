package com.coreeng.supportbot.prtracking.source;

import com.coreeng.supportbot.config.PrTrackingProps;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriUtils;

/**
 * GitLab adapter for {@link PrSourceClient}. Talks to the GitLab v4 REST API via Spring
 * {@link RestClient}. All connection details (apiBaseUrl, token) are resolved per call from
 * {@link PrTrackingProps} so per-repo overrides work without rebuilding the client.
 *
 * <p>Project paths are URL-path-segment encoded (slashes become {@code %2F}) before being placed
 * in API URLs, which is how GitLab addresses projects by full path.
 */
public class GitLabPrSourceClient implements PrSourceClient {

    private static final Logger LOG = LoggerFactory.getLogger(GitLabPrSourceClient.class);

    // GitLab's max per_page is 100. Going lower would just multiply round-trips.
    private static final int PAGE_SIZE = 100;

    private final RestClient restClient;
    private final PrTrackingProps props;
    private final GitLabGroupMemberCache groupMemberCache;

    // default_branch is a per-project, operator-stable value. Cached alongside group members so a
    // typical poll cycle that hits fetchFileContents doesn't add a round-trip per call. Same TTL as
    // sla-discovery so operators only have one knob to turn.
    private final Cache<ProjectKey, String> defaultBranchCache;

    public GitLabPrSourceClient(RestClient restClient, PrTrackingProps props, GitLabGroupMemberCache groupMemberCache) {
        this.restClient = restClient;
        this.props = props;
        this.groupMemberCache = groupMemberCache;
        Duration ttl = Objects.requireNonNull(props.slaDiscovery().cache(), "slaDiscovery.cache must not be null");
        this.defaultBranchCache =
                Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(1_000).build();
    }

    @Override
    public Provider provider() {
        return Provider.GITLAB;
    }

    @Override
    public PrMetadata fetchPullRequest(RepoCoord coord, int prNumber) {
        expectGitLab(coord);
        GitLabConnection conn = resolveConnection(coord.name());
        String projectSegment = UriUtils.encodePathSegment(coord.name(), StandardCharsets.UTF_8);

        MergeRequestDto mr = get(
                conn,
                "/projects/" + projectSegment + "/merge_requests/" + prNumber,
                MergeRequestDto.class,
                "MR %s!%d".formatted(coord.name(), prNumber));
        if (mr == null) {
            throw new GitLabApiException(0, "GitLab returned no body for MR %s!%d".formatted(coord.name(), prNumber));
        }
        Instant createdAt = mr.createdAt();
        if (createdAt == null) {
            throw new GitLabApiException(
                    0, "GitLab returned null created_at for MR %s!%d".formatted(coord.name(), prNumber));
        }
        String stateValue = mr.state();
        if (stateValue == null) {
            throw new GitLabApiException(
                    0, "GitLab returned null state for MR %s!%d".formatted(coord.name(), prNumber));
        }

        PrMetadata.PrState state = mapState(stateValue);
        Boolean mergeable = mapMergeable(mr.detailedMergeStatus());

        // Approvals are only meaningful while the MR is open. Skip the extra call after close/merge
        // to mirror Hub4jGitHubClient's behaviour and avoid unnecessary GitLab API quota burn.
        List<Review> reviews;
        if (state == PrMetadata.PrState.OPEN) {
            ApprovalsDto approvals = get(
                    conn,
                    "/projects/" + projectSegment + "/merge_requests/" + prNumber + "/approvals",
                    ApprovalsDto.class,
                    "approvals %s!%d".formatted(coord.name(), prNumber));
            // updated_at is the best proxy for "when was this last approved" without a Notes API
            // call. Imprecise (it bumps on any MR change), but consistent and always present.
            Instant updatedAt = mr.updatedAt();
            Instant approvalProxy = updatedAt != null ? updatedAt : createdAt;
            reviews = mapApprovals(approvals, approvalProxy);
        } else {
            reviews = List.of();
        }

        return new PrMetadata(coord, prNumber, createdAt, state, mergeable, List.of(), reviews);
    }

    @Override
    public @Nullable String fetchFileContents(RepoCoord coord, String path) {
        expectGitLab(coord);
        GitLabConnection conn = resolveConnection(coord.name());
        String projectSegment = UriUtils.encodePathSegment(coord.name(), StandardCharsets.UTF_8);
        String defaultBranch = resolveDefaultBranch(conn, coord.name(), projectSegment);
        String fileSegment = UriUtils.encodePathSegment(path, StandardCharsets.UTF_8);

        String uri = "/projects/" + projectSegment + "/repository/files/" + fileSegment + "/raw?ref="
                + UriUtils.encodeQueryParam(defaultBranch, StandardCharsets.UTF_8);
        try {
            return restClient
                    .get()
                    .uri(URI.create(conn.apiBaseUrl() + uri))
                    .header("PRIVATE-TOKEN", conn.token())
                    .retrieve()
                    .body(String.class);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (RestClientResponseException e) {
            throw new GitLabApiException(
                    e.getStatusCode().value(),
                    "GitLab API %d fetching %s from %s"
                            .formatted(e.getStatusCode().value(), path, coord.name()),
                    e);
        } catch (RestClientException e) {
            throw new GitLabApiException(
                    0, "GitLab API call failed fetching %s from %s".formatted(path, coord.name()), e);
        }
    }

    @Override
    public List<String> listChangedFiles(RepoCoord coord, int prNumber) {
        expectGitLab(coord);
        GitLabConnection conn = resolveConnection(coord.name());
        String projectSegment = UriUtils.encodePathSegment(coord.name(), StandardCharsets.UTF_8);

        ChangesDto changes = get(
                conn,
                "/projects/" + projectSegment + "/merge_requests/" + prNumber + "/changes",
                ChangesDto.class,
                "changes %s!%d".formatted(coord.name(), prNumber));
        if (changes == null || changes.changes() == null) {
            return List.of();
        }
        return changes.changes().stream()
                .map(c -> c.newPath() != null ? c.newPath() : c.oldPath())
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public List<String> resolveTeamMembers(RepoCoord coord, String teamRef) {
        expectGitLab(coord);
        GitLabConnection conn = resolveConnection(coord.name());
        return groupMemberCache.getMembers(conn.apiBaseUrl(), teamRef, () -> fetchGroupMembers(conn, teamRef));
    }

    private List<String> fetchGroupMembers(GitLabConnection conn, String groupPath) {
        String groupSegment = UriUtils.encodePathSegment(groupPath, StandardCharsets.UTF_8);
        List<String> all = new ArrayList<>();
        int page = 1;
        while (true) {
            // /members/all returns direct + inherited members, matching GitHub team semantics where
            // nested membership counts as membership.
            String uri = "/groups/" + groupSegment + "/members/all?per_page=" + PAGE_SIZE + "&page=" + page;
            List<MemberDto> batch;
            try {
                batch = restClient
                        .get()
                        .uri(URI.create(conn.apiBaseUrl() + uri))
                        .header("PRIVATE-TOKEN", conn.token())
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<MemberDto>>() {});
            } catch (RestClientResponseException e) {
                throw new GitLabApiException(
                        e.getStatusCode().value(),
                        "GitLab API %d listing members for group %s"
                                .formatted(e.getStatusCode().value(), groupPath),
                        e);
            } catch (RestClientException e) {
                throw new GitLabApiException(
                        0, "GitLab API call failed listing members for group %s".formatted(groupPath), e);
            }
            if (batch == null || batch.isEmpty()) {
                break;
            }
            for (MemberDto m : batch) {
                if (m.username() != null) {
                    all.add(m.username());
                }
            }
            if (batch.size() < PAGE_SIZE) {
                break;
            }
            page++;
        }
        return List.copyOf(all);
    }

    private String resolveDefaultBranch(GitLabConnection conn, String projectName, String projectSegment) {
        ProjectKey key = new ProjectKey(conn.apiBaseUrl(), projectName);
        return Objects.requireNonNull(
                defaultBranchCache.get(key, k -> fetchDefaultBranch(conn, projectName, projectSegment)),
                "GitLab returned null default_branch for project " + projectName);
    }

    private String fetchDefaultBranch(GitLabConnection conn, String projectName, String projectSegment) {
        ProjectDto project = get(conn, "/projects/" + projectSegment, ProjectDto.class, "project " + projectName);
        String defaultBranch = project != null ? project.defaultBranch() : null;
        if (defaultBranch == null || defaultBranch.isBlank()) {
            throw new GitLabApiException(0, "GitLab returned no default_branch for project %s".formatted(projectName));
        }
        return defaultBranch;
    }

    private <T> @Nullable T get(GitLabConnection conn, String uriPath, Class<T> type, String contextForLog) {
        try {
            return restClient
                    .get()
                    .uri(URI.create(conn.apiBaseUrl() + uriPath))
                    .header("PRIVATE-TOKEN", conn.token())
                    .retrieve()
                    .body(type);
        } catch (RestClientResponseException e) {
            LOG.debug(
                    "GitLab API {} fetching {}: {}",
                    e.getStatusCode().value(),
                    contextForLog,
                    e.getResponseBodyAsString());
            throw new GitLabApiException(
                    e.getStatusCode().value(),
                    "GitLab API %d fetching %s".formatted(e.getStatusCode().value(), contextForLog),
                    e);
        } catch (RestClientException e) {
            throw new GitLabApiException(0, "GitLab API call failed fetching %s".formatted(contextForLog), e);
        }
    }

    /**
     * Walks the configured repositories to find the matching GitLab entry, then layers per-repo
     * overrides on top of the global {@code gitlab} block. The validation in
     * {@link PrTrackingProps} guarantees at least one of the two provides a token; here we just
     * surface a clear error if the lookup somehow finds neither (defensive — should never fire).
     */
    private GitLabConnection resolveConnection(String repoName) {
        PrTrackingProps.Repository repoCfg = props.repositories().stream()
                .filter(r -> r.name().equalsIgnoreCase(repoName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No PR-tracking repository configured for: " + repoName));
        if (repoCfg.provider() != Provider.GITLAB) {
            throw new IllegalStateException(
                    "Repository " + repoName + " is configured for provider " + repoCfg.provider() + ", not GitLab");
        }
        PrTrackingProps.Gitlab perRepo = repoCfg.gitlab();
        PrTrackingProps.Gitlab global = props.gitlab();

        String apiBaseUrl = pickNonBlank(
                perRepo != null ? perRepo.apiBaseUrl() : null, global != null ? global.apiBaseUrl() : null);
        String token = pickNonBlank(perRepo != null ? perRepo.token() : null, global != null ? global.token() : null);

        if (apiBaseUrl == null) {
            throw new IllegalStateException(
                    "No gitlab.api-base-url resolvable for repo " + repoName + " (global or per-repo)");
        }
        if (token == null) {
            throw new IllegalStateException(
                    "No gitlab.token resolvable for repo " + repoName + " (global or per-repo)");
        }
        return new GitLabConnection(apiBaseUrl, token);
    }

    private static @Nullable String pickNonBlank(@Nullable String preferred, @Nullable String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return null;
    }

    private static void expectGitLab(RepoCoord coord) {
        if (coord.provider() != Provider.GITLAB) {
            throw new IllegalArgumentException(
                    "GitLabPrSourceClient called with non-GitLab coord: " + coord.provider());
        }
    }

    private static PrMetadata.PrState mapState(String state) {
        return switch (state.toLowerCase(Locale.ROOT)) {
            case "opened" -> PrMetadata.PrState.OPEN;
            case "merged" -> PrMetadata.PrState.MERGED;
            case "closed", "locked" -> PrMetadata.PrState.CLOSED;
            default -> throw new GitLabApiException(0, "Unrecognised GitLab MR state: " + state);
        };
    }

    /**
     * Maps GitLab's {@code detailed_merge_status} field to the provider-neutral tri-state used by
     * {@link PrMetadata#mergeable()}. Treats {@code mergeable} and {@code ci_still_running} as
     * mergeable — this matches the GitHub behaviour of ignoring required-check state.
     *
     * <p>A null/missing value (older GitLab versions, or transient between status calculations)
     * yields a {@code null} tri-state — same semantics as GitHub when mergeability hasn't been
     * computed yet, so the lifecycle poller will retry.
     */
    private static @Nullable Boolean mapMergeable(@Nullable String detailedMergeStatus) {
        if (detailedMergeStatus == null) {
            return null;
        }
        return switch (detailedMergeStatus.toLowerCase(Locale.ROOT)) {
            case "mergeable", "ci_still_running" -> Boolean.TRUE;
            default -> Boolean.FALSE;
        };
    }

    private static List<Review> mapApprovals(@Nullable ApprovalsDto approvals, Instant submittedAtProxy) {
        if (approvals == null || approvals.approvedBy() == null) {
            return List.of();
        }
        return approvals.approvedBy().stream()
                .map(ApprovedByEntryDto::user)
                .filter(Objects::nonNull)
                .map(UserRefDto::username)
                .filter(Objects::nonNull)
                .map(u -> new Review(u, Review.ReviewState.APPROVED, submittedAtProxy))
                .toList();
    }

    private record GitLabConnection(String apiBaseUrl, String token) {}

    private record ProjectKey(String apiBaseUrl, String projectPath) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MergeRequestDto(
            @JsonProperty("created_at") @Nullable Instant createdAt,
            @JsonProperty("updated_at") @Nullable Instant updatedAt,
            @Nullable String state,
            @JsonProperty("detailed_merge_status") @Nullable String detailedMergeStatus) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ApprovalsDto(
            @JsonProperty("approved_by") @Nullable List<ApprovedByEntryDto> approvedBy) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ApprovedByEntryDto(@Nullable UserRefDto user) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record UserRefDto(@Nullable String username) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChangesDto(@Nullable List<ChangeEntryDto> changes) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChangeEntryDto(
            @JsonProperty("new_path") @Nullable String newPath,
            @JsonProperty("old_path") @Nullable String oldPath) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProjectDto(
            @JsonProperty("default_branch") @Nullable String defaultBranch) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MemberDto(@Nullable String username) {}
}
