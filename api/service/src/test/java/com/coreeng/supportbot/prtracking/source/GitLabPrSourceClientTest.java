package com.coreeng.supportbot.prtracking.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withRawStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.coreeng.supportbot.config.PrTrackingProps;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GitLabPrSourceClientTest {

    private static final String API = "https://gitlab.example.com/api/v4";
    private static final String TOKEN = "glpat-secret";
    private static final String REPO = "my-group/sub-group/project";
    // %2F-encoded form of "my-group/sub-group/project" — GitLab requires path segment encoding here.
    private static final String REPO_ENC = "my-group%2Fsub-group%2Fproject";

    private MockRestServiceServer server;
    private GitLabPrSourceClient client;
    private GitLabGroupMemberCache memberCache;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .messageConverters(ImmutableList.of(
                        new MappingJackson2HttpMessageConverter(
                                new ObjectMapper().registerModule(new JavaTimeModule())),
                        new org.springframework.http.converter.StringHttpMessageConverter(
                                java.nio.charset.StandardCharsets.UTF_8)));
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        memberCache = new GitLabGroupMemberCache(Duration.ofMinutes(5));
        client = new GitLabPrSourceClient(restClient, propsWithSingleGitLabRepo(), memberCache);
    }

    @Test
    void reportsProviderAsGitlab() {
        assertThat(client.provider()).isEqualTo(Provider.GITLAB);
    }

    @Test
    void fetchPullRequestMapsOpenMrAndApprovals() {
        server.expect(requestTo(API + "/projects/" + REPO_ENC + "/merge_requests/42"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("PRIVATE-TOKEN", TOKEN))
                .andRespond(withSuccess("""
                        {
                          "iid": 42,
                          "state": "opened",
                          "created_at": "2026-01-01T10:00:00Z",
                          "updated_at": "2026-01-02T11:00:00Z",
                          "detailed_merge_status": "mergeable"
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/projects/" + REPO_ENC + "/merge_requests/42/approvals"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "approved_by": [
                            {"user": {"username": "alice"}},
                            {"user": {"username": "bob"}}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        PrMetadata metadata = client.fetchPullRequest(RepoCoord.gitlab(REPO), 42);

        assertThat(metadata.number()).isEqualTo(42);
        assertThat(metadata.state()).isEqualTo(PrMetadata.PrState.OPEN);
        assertThat(metadata.createdAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
        assertThat(metadata.mergeable()).isTrue();
        assertThat(metadata.requestedTeamReviewerLogins()).isEmpty();
        assertThat(metadata.reviews())
                .extracting(Review::userLogin, Review::state)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("alice", Review.ReviewState.APPROVED),
                        org.assertj.core.groups.Tuple.tuple("bob", Review.ReviewState.APPROVED));
        assertThat(metadata.reviews().get(0).submittedAt()).isEqualTo(Instant.parse("2026-01-02T11:00:00Z"));
        server.verify();
    }

    @Test
    void fetchPullRequestSkipsApprovalsCallWhenMerged() {
        server.expect(requestTo(API + "/projects/" + REPO_ENC + "/merge_requests/7"))
                .andRespond(withSuccess("""
                        {
                          "iid": 7,
                          "state": "merged",
                          "created_at": "2026-01-01T10:00:00Z",
                          "updated_at": "2026-01-03T10:00:00Z",
                          "detailed_merge_status": "not_open"
                        }
                        """, MediaType.APPLICATION_JSON));

        PrMetadata metadata = client.fetchPullRequest(RepoCoord.gitlab(REPO), 7);

        assertThat(metadata.state()).isEqualTo(PrMetadata.PrState.MERGED);
        assertThat(metadata.reviews()).isEmpty();
        server.verify();
    }

    @Test
    void fetchPullRequestTreatsCiStillRunningAsMergeable() {
        server.expect(requestTo(API + "/projects/" + REPO_ENC + "/merge_requests/5"))
                .andRespond(withSuccess("""
                        {
                          "iid": 5, "state": "opened",
                          "created_at": "2026-01-01T00:00:00Z",
                          "updated_at": "2026-01-01T00:00:00Z",
                          "detailed_merge_status": "ci_still_running"
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/projects/" + REPO_ENC + "/merge_requests/5/approvals"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client.fetchPullRequest(RepoCoord.gitlab(REPO), 5).mergeable())
                .isTrue();
    }

    @Test
    void fetchPullRequestTreatsConflictAsNotMergeable() {
        server.expect(requestTo(API + "/projects/" + REPO_ENC + "/merge_requests/6"))
                .andRespond(withSuccess("""
                        {
                          "iid": 6, "state": "opened",
                          "created_at": "2026-01-01T00:00:00Z",
                          "updated_at": "2026-01-01T00:00:00Z",
                          "detailed_merge_status": "conflict"
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/projects/" + REPO_ENC + "/merge_requests/6/approvals"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client.fetchPullRequest(RepoCoord.gitlab(REPO), 6).mergeable())
                .isFalse();
    }

    @Test
    void fetchPullRequestLeavesMergeableNullWhenStatusAbsent() {
        server.expect(requestTo(API + "/projects/" + REPO_ENC + "/merge_requests/9"))
                .andRespond(withSuccess("""
                        {
                          "iid": 9, "state": "opened",
                          "created_at": "2026-01-01T00:00:00Z",
                          "updated_at": "2026-01-01T00:00:00Z"
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/projects/" + REPO_ENC + "/merge_requests/9/approvals"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client.fetchPullRequest(RepoCoord.gitlab(REPO), 9).mergeable())
                .isNull();
    }

    @Test
    void fetchPullRequestRejectsUnknownState() {
        server.expect(requestTo(API + "/projects/" + REPO_ENC + "/merge_requests/1"))
                .andRespond(withSuccess("""
                        {
                          "iid": 1, "state": "weird-new-state",
                          "created_at": "2026-01-01T00:00:00Z",
                          "updated_at": "2026-01-01T00:00:00Z"
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchPullRequest(RepoCoord.gitlab(REPO), 1))
                .isInstanceOf(GitLabApiException.class)
                .hasMessageContaining("Unrecognised GitLab MR state");
    }

    @Test
    void fetchPullRequestWrapsHttpErrors() {
        server.expect(requestTo(API + "/projects/" + REPO_ENC + "/merge_requests/404"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.fetchPullRequest(RepoCoord.gitlab(REPO), 404))
                .isInstanceOf(GitLabApiException.class)
                .satisfies(
                        e -> assertThat(((GitLabApiException) e).statusCode()).isEqualTo(404));
    }

    @Test
    void fetchFileContentsResolvesDefaultBranchThenFetchesRaw() {
        server.expect(requestTo(API + "/projects/" + REPO_ENC))
                .andRespond(withSuccess("{\"default_branch\": \"main\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/projects/" + REPO_ENC + "/repository/files/path%2Fto%2Ffile.yaml/raw?ref=main"))
                .andExpect(header("PRIVATE-TOKEN", TOKEN))
                .andRespond(withSuccess("default: 24h\n", MediaType.TEXT_PLAIN));

        String content = client.fetchFileContents(RepoCoord.gitlab(REPO), "path/to/file.yaml");

        assertThat(content).isEqualTo("default: 24h\n");
        server.verify();
    }

    @Test
    void fetchFileContentsCachesDefaultBranchAcrossCalls() {
        // First call: 1 project lookup + 1 file fetch.
        server.expect(requestTo(API + "/projects/" + REPO_ENC))
                .andRespond(withSuccess("{\"default_branch\": \"main\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/projects/" + REPO_ENC + "/repository/files/a.yaml/raw?ref=main"))
                .andRespond(withSuccess("a", MediaType.TEXT_PLAIN));
        // Second call: only the file fetch — default_branch must come from cache.
        server.expect(requestTo(API + "/projects/" + REPO_ENC + "/repository/files/b.yaml/raw?ref=main"))
                .andRespond(withSuccess("b", MediaType.TEXT_PLAIN));

        client.fetchFileContents(RepoCoord.gitlab(REPO), "a.yaml");
        client.fetchFileContents(RepoCoord.gitlab(REPO), "b.yaml");

        server.verify();
    }

    @Test
    void fetchFileContentsReturnsNullOn404() {
        server.expect(requestTo(API + "/projects/" + REPO_ENC))
                .andRespond(withSuccess("{\"default_branch\": \"main\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/projects/" + REPO_ENC + "/repository/files/missing.yaml/raw?ref=main"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));

        assertThat(client.fetchFileContents(RepoCoord.gitlab(REPO), "missing.yaml"))
                .isNull();
    }

    @Test
    void listChangedFilesPrefersNewPathFallsBackToOldPath() {
        // old_path is set + new_path null when a file is deleted by the MR; cover both shapes.
        server.expect(requestTo(API + "/projects/" + REPO_ENC + "/merge_requests/12/changes"))
                .andRespond(withSuccess("""
                        {
                          "changes": [
                            {"new_path": "src/new.java", "old_path": "src/new.java"},
                            {"new_path": null, "old_path": "deleted.txt"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<String> files = client.listChangedFiles(RepoCoord.gitlab(REPO), 12);

        assertThat(files).containsExactly("src/new.java", "deleted.txt");
    }

    @Test
    void resolveTeamMembersDelegatesToCacheAndPaginates() {
        // 100 members on page 1 forces a second page; 5 on page 2 stops the loop. Verifies the
        // pagination-until-short-page exit condition.
        StringBuilder page1 = new StringBuilder("[");
        for (int i = 0; i < 100; i++) {
            if (i > 0) page1.append(",");
            page1.append("{\"username\": \"user").append(i).append("\"}");
        }
        page1.append("]");

        server.expect(requestTo(API + "/groups/my-group%2Fsub-group/members/all?per_page=100&page=1"))
                .andRespond(withSuccess(page1.toString(), MediaType.APPLICATION_JSON));
        server.expect(requestTo(API + "/groups/my-group%2Fsub-group/members/all?per_page=100&page=2"))
                .andRespond(withSuccess(
                        "[{\"username\": \"late1\"}, {\"username\": \"late2\"}]", MediaType.APPLICATION_JSON));

        List<String> members = client.resolveTeamMembers(RepoCoord.gitlab(REPO), "my-group/sub-group");

        assertThat(members).hasSize(102).contains("user0", "user99", "late1", "late2");

        // Second call must hit the cache, not the network.
        List<String> cached = client.resolveTeamMembers(RepoCoord.gitlab(REPO), "my-group/sub-group");
        assertThat(cached).isSameAs(members);
        server.verify();
    }

    @Test
    void resolveTeamMembersWrapsHttpErrors() {
        server.expect(requestTo(API + "/groups/missing/members/all?per_page=100&page=1"))
                .andRespond(withRawStatus(401));

        assertThatThrownBy(() -> client.resolveTeamMembers(RepoCoord.gitlab(REPO), "missing"))
                .isInstanceOf(GitLabApiException.class)
                .satisfies(
                        e -> assertThat(((GitLabApiException) e).statusCode()).isEqualTo(401));
    }

    @Test
    void rejectsNonGitLabCoord() {
        assertThatThrownBy(() -> client.fetchPullRequest(RepoCoord.github("org/repo"), 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-GitLab coord");
    }

    @Test
    void perRepoOverridesWinOverGlobal() {
        // Per-repo override on the same repo points at a different host + token; the source client
        // must pick those up. Verifies resolveConnection's precedence rule end-to-end.
        String overrideApi = "https://override.example/api/v4";
        String overrideToken = "override-token";
        PrTrackingProps.Repository repo = new PrTrackingProps.Repository(
                REPO,
                "my-team",
                Provider.GITLAB,
                null,
                "my-group/sub-group",
                List.of(),
                new PrTrackingProps.Sla(null, Duration.ofHours(24), null),
                new PrTrackingProps.Gitlab(overrideApi, overrideToken),
                null);
        PrTrackingProps props = new PrTrackingProps(
                true,
                "0 * * * * *",
                "pr",
                List.of("support"),
                "team",
                "days",
                List.of(repo),
                PrTrackingProps.GitHub.defaultTokenModeConfig(),
                new PrTrackingProps.Gitlab(API, TOKEN),
                new PrTrackingProps.SlaDiscovery(Duration.ofHours(1)));

        RestClient.Builder builder = RestClient.builder()
                .messageConverters(ImmutableList.of(
                        new MappingJackson2HttpMessageConverter(
                                new ObjectMapper().registerModule(new JavaTimeModule())),
                        new org.springframework.http.converter.StringHttpMessageConverter(
                                java.nio.charset.StandardCharsets.UTF_8)));
        MockRestServiceServer overrideServer =
                MockRestServiceServer.bindTo(builder).build();
        GitLabPrSourceClient overrideClient =
                new GitLabPrSourceClient(builder.build(), props, new GitLabGroupMemberCache(Duration.ofMinutes(5)));

        overrideServer
                .expect(requestTo(overrideApi + "/projects/" + REPO_ENC + "/merge_requests/1"))
                .andExpect(header("PRIVATE-TOKEN", overrideToken))
                .andRespond(withSuccess("""
                        {
                          "iid": 1, "state": "opened",
                          "created_at": "2026-01-01T00:00:00Z",
                          "updated_at": "2026-01-01T00:00:00Z",
                          "detailed_merge_status": "mergeable"
                        }
                        """, MediaType.APPLICATION_JSON));
        overrideServer
                .expect(requestTo(overrideApi + "/projects/" + REPO_ENC + "/merge_requests/1/approvals"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        overrideClient.fetchPullRequest(RepoCoord.gitlab(REPO), 1);
        overrideServer.verify();
    }

    private static PrTrackingProps propsWithSingleGitLabRepo() {
        PrTrackingProps.Repository repo = new PrTrackingProps.Repository(
                REPO,
                "my-team",
                Provider.GITLAB,
                null,
                "my-group/sub-group",
                List.of(),
                new PrTrackingProps.Sla(null, Duration.ofHours(24), null),
                null,
                null);
        return new PrTrackingProps(
                true,
                "0 * * * * *",
                "pr",
                List.of("support"),
                "team",
                "days",
                List.of(repo),
                PrTrackingProps.GitHub.defaultTokenModeConfig(),
                new PrTrackingProps.Gitlab(API, TOKEN),
                new PrTrackingProps.SlaDiscovery(Duration.ofHours(1)));
    }
}
