package com.coreeng.supportbot.testkit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.text.StringSubstitutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wiremock implementation for Slack service.
 * Handles mocking of Slack API endpoints.
 */
public class SlackWiremock implements WireMockBackend {
    private static final Logger LOGGER = LoggerFactory.getLogger(SlackWiremock.class);

    private final Config.SlackMock config;
    private final WireMockBackend backend;
    private final Set<UUID> permanentStubIds = new HashSet<>();

    public SlackWiremock(Config.SlackMock config) {
        this.config = config;
        backend = createBackend(config);
    }

    @Override
    public void start() {
        backend.start();
        resetAll();
        permanentStubIds.clear();
        setupAppInitMocks();
        capturePermanentStubs();
        LOGGER.info("Started Slack Wiremock in {} mode on port {}", config.wireMockMode(), port());
    }

    @Override
    public void stop() {
        try {
            backend.stop();
        } finally {
            permanentStubIds.clear();
        }
        LOGGER.info("Stopped Slack Wiremock");
    }

    @Override
    public int port() {
        return backend.port();
    }

    @Override
    public void resetAll() {
        backend.resetAll();
    }

    public StubMapping stubFor(MappingBuilder mappingBuilder) {
        return givenThat(mappingBuilder);
    }

    @Override
    public List<LoggedRequest> findAll(RequestPatternBuilder requestPatternBuilder) {
        return backend.findAll(requestPatternBuilder);
    }

    @Override
    public List<ServeEvent> getAllServeEvents() {
        return backend.getAllServeEvents();
    }

    @Override
    public List<ServeEvent> getServeEventsFor(StubMapping stubMapping) {
        return backend.getServeEventsFor(stubMapping);
    }

    private static WireMockBackend createBackend(Config.SlackMock config) {
        return switch (config.wireMockMode()) {
            case EMBEDDED -> new EmbeddedWireMockBackend(config);
            case REMOTE -> new RemoteWireMockBackend(config);
        };
    }

    @Override
    public StubMapping givenThat(MappingBuilder mappingBuilder) {
        return backend.givenThat(mappingBuilder);
    }

    @Override
    public List<StubMapping> getStubMappings() {
        return backend.getStubMappings();
    }

    @Override
    public void removeStubMapping(StubMapping stubMapping) {
        backend.removeStubMapping(stubMapping);
    }

    @Override
    public List<LoggedRequest> findAllUnmatchedRequests() {
        return backend.findAllUnmatchedRequests();
    }

    @Override
    public void resetRequests() {
        backend.resetRequests();
    }

    private void setupAppInitMocks() {
        LOGGER.info("Setting up initial Slack API stubs");
        stubAuthTest("initial mock");
        stubUsersInfoDefault("initial users.info mock");
        // Catch-all for hub4j user profile lookups triggered by review processing
        givenThat(get(urlMatching("/users/.*"))
                .withName("GitHub user lookup catch-all")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"login\":\"{{request.pathSegments.[1]}}\",\"id\":1,\"type\":\"User\"}")));
        // Catch-all for conversations.history (triggered by createTicket test API fetching query text)
        givenThat(post("/api/conversations.history")
                .withName("conversations history catch-all")
                .willReturn(okJson("{\"ok\":true,\"messages\":[],\"has_more\":false}")));
        // Catch-alls for the PR lifecycle poller: when triggered (manually via the test endpoint
        // or via Spring's cron) it walks every active pr_tracking row, so stray records from other
        // tests can land on wiremock without a per-test stub. Returning an "open, no reviews"
        // shape keeps the poller happy without affecting behaviour for tests that stub explicitly.
        givenThat(get(urlMatching("/repos/[^/]+/[^/]+/pulls/[0-9]+/reviews"))
                .withName("GitHub PR reviews catch-all")
                .willReturn(okJson("[]")));
        givenThat(get(urlMatching("/repos/[^/]+/[^/]+/pulls/[0-9]+"))
                .withName("GitHub PR catch-all")
                .willReturn(okJson("{\"state\":\"open\",\"number\":0,\"requested_teams\":[],"
                        + "\"mergeable\":false,\"mergeable_state\":\"unknown\","
                        + "\"created_at\":\"2026-01-01T00:00:00Z\",\"title\":\"\","
                        + "\"user\":{\"login\":\"test\"}}")));
        givenThat(get(urlMatching("/repos/[^/]+/[^/]+"))
                .withName("GitHub repo metadata catch-all")
                .willReturn(okJson("{\"id\":1,\"name\":\"catchall\",\"full_name\":\"catchall/catchall\","
                        + "\"owner\":{\"login\":\"catchall\"},\"private\":false}")));
    }

    private void capturePermanentStubs() {
        getStubMappings().forEach(stub -> permanentStubIds.add(stub.getId()));
        LOGGER.info("Captured {} permanent stubs", permanentStubIds.size());
    }

    /**
     * Asserts that no test stubs remain after a test.
     * Permanent stubs (set up at server start) are excluded from this check.
     *
     * @throws AssertionError if any test stubs remain
     */
    public void assertNoTestStubsRemaining() {
        List<StubMapping> remainingTestStubs = getStubMappings().stream()
                .filter(stub -> !permanentStubIds.contains(stub.getId()))
                .toList();

        if (!remainingTestStubs.isEmpty()) {
            String details = remainingTestStubs.stream()
                    .map(s -> "  - " + s.getName() + " (" + s.getRequest().getUrl() + ")")
                    .collect(Collectors.joining("\n"));
            fail("Test left %d stubs uncleaned:\n%s".formatted(remainingTestStubs.size(), details));
        }
    }

    /**
     * Removes all test stubs, leaving only permanent stubs.
     * This ensures a clean slate for the next test.
     */
    public void cleanupTestStubs() {
        List<StubMapping> testStubs = getStubMappings().stream()
                .filter(stub -> !permanentStubIds.contains(stub.getId()))
                .toList();

        for (StubMapping stub : testStubs) {
            removeStubMapping(stub);
        }

        if (!testStubs.isEmpty()) {
            LOGGER.debug("Cleaned up {} test stubs", testStubs.size());
        }
    }

    /**
     * Asserts that no unhandled requests were made during the test.
     * All requests should be matched by a stub.
     *
     * @throws AssertionError if any requests were not matched
     */
    public void assertNoUnhandledRequests() {
        var unmatchedRequests = findAllUnmatchedRequests();

        if (!unmatchedRequests.isEmpty()) {
            String details = unmatchedRequests.stream()
                    .map(req -> "  - " + req.getMethod().getName() + " " + req.getUrl())
                    .collect(Collectors.joining("\n"));
            fail("Test had %d unhandled requests:\n%s".formatted(unmatchedRequests.size(), details));
        }
    }

    /**
     * Clears the request journal to prevent interference between tests.
     */
    public void clearRequestJournal() {
        resetRequests();

        LOGGER.debug("Cleared request journal");
    }

    public void stubAuthTest(String description) {
        givenThat(post("/api/auth.test")
                .withName(description)
                .willReturn(okJson(new StringSubstitutor(Map.of(
                                "url", config.serverUrl(),
                                "team", config.team(),
                                "teamId", config.teamId(),
                                "userId", config.supportBotUserId(),
                                "botId", config.supportBotId()))
                        .replace("""
                {
                  "ok":true,
                  "url":"${url}",
                  "team":"${team}",
                  "user":"core_support",
                  "team_id":"${teamId}",
                  "user_id":"${userId}",
                  "bot_id":"${botId}",
                  "is_enterprise_install":false}"""))));
    }

    public Stub stubConversationsOpen(String description, String expectedUserId) {
        String channelId = "D" + expectedUserId;
        StubMapping stubMapping = givenThat(post("/api/conversations.open")
                .withName(description)
                .withFormParam("users", equalTo(expectedUserId))
                .willReturn(okJson("""
                {
                  "ok": true,
                  "channel": {
                    "id": "%s"
                  }
                }""".formatted(channelId))));
        return Stub.builder()
                .mapping(stubMapping)
                .wireMockServer(this)
                .description(description)
                .build();
    }

    public Stub stubChatPostMessage(String description, String expectedChannelId) {
        StubMapping stubMapping = givenThat(post("/api/chat.postMessage")
                .withName(description)
                .withFormParam("channel", equalTo(expectedChannelId))
                .willReturn(okJson("""
                {
                  "ok": true,
                  "channel": "%s",
                  "ts": "1234567890.123456",
                  "message": {
                    "type": "message",
                    "text": "UNSET_BY_TESTS"
                  }
                }""".formatted(expectedChannelId))));
        return Stub.builder()
                .mapping(stubMapping)
                .wireMockServer(this)
                .description(description)
                .build();
    }

    /**
     * Stubs GitHub API GET /repos/{owner}/{repo}/pulls/{number} for PR-tracking functional tests.
     * Use when the app is configured with pr-review-tracking and GITHUB_API_BASE_URL pointing to this wiremock.
     *
     * @param description   stub name for debugging and cleanup
     * @param repositoryName repo in "owner/repo" form (e.g. "test-org/test-repo")
     * @param pullNumber    PR number
     * @param state         "open" or "closed"
     * @param createdAtIso ISO-8601 instant (e.g. "2024-01-15T10:00:00Z")
     */
    // hub4j eagerly calls GET /repos/{owner}/{repo} before any repo operation,
    // so every GitHub stub needs this or WireMock will reject the request.
    private StubMapping stubRepoMetadata(String description, String repositoryName) {
        String[] parts = repositoryName.split("/", 2);
        String repoBody = """
                {"id":1,"name":"%s","full_name":"%s","owner":{"login":"%s"},"private":false}
                """.formatted(parts[1], repositoryName, parts[0]);
        return givenThat(get("/repos/" + repositoryName)
                .withName(description + " (repo metadata)")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(repoBody)));
    }

    public Stub stubGitHubGetPullRequest(
            String description, String repositoryName, int pullNumber, String state, String createdAtIso) {
        return stubGitHubGetPullRequest(description, repositoryName, pullNumber, state, createdAtIso, false, "[]");
    }

    public Stub stubGitHubGetPullRequest(
            String description,
            String repositoryName,
            int pullNumber,
            String state,
            String createdAtIso,
            boolean mergeable,
            String reviewsJson) {
        StubMapping repoStub = stubRepoMetadata(description, repositoryName);

        String prPath = "/repos/" + repositoryName + "/pulls/" + pullNumber;
        String prBody = """
                {"state":"%s","created_at":"%s","title":"PR title","user":{"login":"test"},"number":%d,"requested_teams":[],"mergeable":%b,"mergeable_state":"unknown"}
                """.formatted(state, createdAtIso, pullNumber, mergeable);
        StubMapping prStub = givenThat(get(prPath)
                .withName(description)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(prBody)));
        StubMapping reviewsStub = givenThat(get(prPath + "/reviews")
                .withName(description + " reviews")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(reviewsJson)));
        return Stub.builder()
                .mapping(prStub)
                .extraMappings(List.of(repoStub, reviewsStub))
                .wireMockServer(this)
                .description(description)
                .build();
    }

    /**
     * Stubs GitHub API GET /repos/{owner}/{repo}/pulls/{number} with an error response.
     * Also stubs the repository metadata call required by hub4j.
     */
    public Stub stubGitHubGetPullRequestError(
            String description, String repositoryName, int pullNumber, int statusCode, String errorMessage) {
        StubMapping repoStub = stubRepoMetadata(description, repositoryName);

        String prPath = "/repos/" + repositoryName + "/pulls/" + pullNumber;
        StubMapping prStub = givenThat(get(prPath)
                .withName(description)
                .willReturn(aResponse()
                        .withStatus(statusCode)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"message":"%s"}
                                """.formatted(errorMessage))));
        return Stub.builder()
                .mapping(prStub)
                .extraMappings(List.of(repoStub))
                .wireMockServer(this)
                .description(description)
                .build();
    }

    public Stub stubGitHubGetFileContent(String description, String repositoryName, String path, String content) {
        StubMapping repoStub = stubRepoMetadata(description, repositoryName);
        String base64 = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
        StubMapping stub = givenThat(get("/repos/" + repositoryName + "/contents/" + path)
                .withName(description)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"content":"%s","encoding":"base64","type":"file"}
                                """.formatted(base64))));
        return Stub.builder()
                .mapping(stub)
                .extraMappings(List.of(repoStub))
                .wireMockServer(this)
                .description(description)
                .build();
    }

    public Stub stubGitHubGetFileContentNotFound(String description, String repositoryName, String path) {
        StubMapping repoStub = stubRepoMetadata(description, repositoryName);
        StubMapping stub = givenThat(get("/repos/" + repositoryName + "/contents/" + path)
                .withName(description)
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"message":"Not Found"}
                                """)));
        return Stub.builder()
                .mapping(stub)
                .extraMappings(List.of(repoStub))
                .wireMockServer(this)
                .description(description)
                .build();
    }

    public Stub stubGitHubListPullRequestFiles(
            String description, String repositoryName, int pullNumber, List<String> fileNames) {
        StubMapping repoStub = stubRepoMetadata(description, repositoryName);
        String filesJson = fileNames.stream().map(f -> """
                        {"filename":"%s","status":"modified"}""".formatted(f)).collect(Collectors.joining(",", "[", "]"));
        StubMapping stub = givenThat(get("/repos/" + repositoryName + "/pulls/" + pullNumber + "/files")
                .withName(description)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(filesJson)));
        return Stub.builder()
                .mapping(stub)
                .extraMappings(List.of(repoStub))
                .wireMockServer(this)
                .description(description)
                .build();
    }

    // ---------------------------------------------------------------------
    // GitLab v4 API stubs (served by the same wiremock at /api/v4/...)
    // ---------------------------------------------------------------------

    /**
     * GitLab project paths are URL-path-segment encoded (slashes become {@code %2F}). This must
     * match {@code UriUtils.encodePathSegment} on the production side or the stub will not match.
     */
    private static String encodeProjectPath(String projectPath) {
        return projectPath.replace("/", "%2F");
    }

    /**
     * Stubs GitLab API GET /api/v4/projects/:project/merge_requests/:iid for MR detection and
     * lifecycle polling.
     */
    public Stub stubGitLabGetMergeRequest(
            String description,
            String projectPath,
            int mrIid,
            String state,
            String detailedMergeStatus,
            String createdAtIso,
            String updatedAtIso) {
        String encoded = encodeProjectPath(projectPath);
        String body = """
                {"iid":%d,"state":"%s","detailed_merge_status":"%s","created_at":"%s","updated_at":"%s","title":"MR title","author":{"username":"test"}}
                """.formatted(mrIid, state, detailedMergeStatus, createdAtIso, updatedAtIso);
        StubMapping stub = givenThat(get("/api/v4/projects/" + encoded + "/merge_requests/" + mrIid)
                .withName(description)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
        return Stub.builder()
                .mapping(stub)
                .wireMockServer(this)
                .description(description)
                .build();
    }

    /**
     * Stubs GitLab API GET /api/v4/projects/:project/merge_requests/:iid/approvals returning the
     * given approver usernames.
     */
    public Stub stubGitLabGetMergeRequestApprovals(
            String description, String projectPath, int mrIid, List<String> approverUsernames) {
        String encoded = encodeProjectPath(projectPath);
        String approvedByJson =
                approverUsernames.stream().map(u -> """
                        {"user":{"username":"%s"}}""".formatted(u)).collect(Collectors.joining(",", "[", "]"));
        String body = """
                {"approved_by":%s}
                """.formatted(approvedByJson);
        StubMapping stub = givenThat(get("/api/v4/projects/" + encoded + "/merge_requests/" + mrIid + "/approvals")
                .withName(description)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
        return Stub.builder()
                .mapping(stub)
                .wireMockServer(this)
                .description(description)
                .build();
    }

    /**
     * Stubs GitLab API GET /api/v4/projects/:project to expose the project's default branch (used
     * before fetching repo files at the raw blob endpoint).
     */
    public Stub stubGitLabGetProject(String description, String projectPath, String defaultBranch) {
        String encoded = encodeProjectPath(projectPath);
        String body = """
                {"default_branch":"%s"}
                """.formatted(defaultBranch);
        StubMapping stub = givenThat(get("/api/v4/projects/" + encoded)
                .withName(description)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
        return Stub.builder()
                .mapping(stub)
                .wireMockServer(this)
                .description(description)
                .build();
    }

    /**
     * Stubs GitLab API GET /api/v4/projects/:project/repository/files/:file/raw?ref=:branch
     * returning the raw file bytes (as text).
     */
    public Stub stubGitLabGetFileRaw(
            String description, String projectPath, String filePath, String branch, String content) {
        String encodedProject = encodeProjectPath(projectPath);
        String encodedFile = encodeProjectPath(filePath);
        StubMapping stub = givenThat(
                get("/api/v4/projects/" + encodedProject + "/repository/files/" + encodedFile + "/raw?ref=" + branch)
                        .withName(description)
                        .willReturn(aResponse()
                                .withStatus(200)
                                .withHeader("Content-Type", "text/plain")
                                .withBody(content)));
        return Stub.builder()
                .mapping(stub)
                .wireMockServer(this)
                .description(description)
                .build();
    }

    /**
     * Stubs a 404 from GitLab's raw-file endpoint, used to test the missing-file branch of
     * {@link com.coreeng.supportbot.prtracking.source.GitLabPrSourceClient#fetchFileContents}.
     */
    public Stub stubGitLabGetFileRawNotFound(String description, String projectPath, String filePath, String branch) {
        String encodedProject = encodeProjectPath(projectPath);
        String encodedFile = encodeProjectPath(filePath);
        StubMapping stub = givenThat(
                get("/api/v4/projects/" + encodedProject + "/repository/files/" + encodedFile + "/raw?ref=" + branch)
                        .withName(description)
                        .willReturn(aResponse()
                                .withStatus(404)
                                .withHeader("Content-Type", "application/json")
                                .withBody("""
                                {"message":"404 File Not Found"}
                                """)));
        return Stub.builder()
                .mapping(stub)
                .wireMockServer(this)
                .description(description)
                .build();
    }

    /**
     * Stubs GitLab API GET /api/v4/projects/:project/merge_requests/:iid/changes for path-scoped
     * SLA overrides.
     */
    public Stub stubGitLabListChanges(String description, String projectPath, int mrIid, List<String> filenames) {
        String encoded = encodeProjectPath(projectPath);
        String changesJson =
                filenames.stream().map(f -> """
                        {"new_path":"%s","old_path":"%s"}""".formatted(f, f)).collect(Collectors.joining(",", "[", "]"));
        String body = """
                {"changes":%s}
                """.formatted(changesJson);
        StubMapping stub = givenThat(get("/api/v4/projects/" + encoded + "/merge_requests/" + mrIid + "/changes")
                .withName(description)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
        return Stub.builder()
                .mapping(stub)
                .wireMockServer(this)
                .description(description)
                .build();
    }

    /**
     * Stubs GitLab API GET /api/v4/groups/:group/members/all?per_page=100&page=1 returning the
     * given usernames.
     */
    public Stub stubGitLabGetGroupMembers(String description, String groupPath, List<String> usernames) {
        String encoded = encodeProjectPath(groupPath);
        String body = usernames.stream().map(u -> """
                        {"username":"%s"}""".formatted(u)).collect(Collectors.joining(",", "[", "]"));
        StubMapping stub = givenThat(get("/api/v4/groups/" + encoded + "/members/all?per_page=100&page=1")
                .withName(description)
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
        return Stub.builder()
                .mapping(stub)
                .wireMockServer(this)
                .description(description)
                .build();
    }

    public Stub stubReactionAdd(ReactionAddedExpectation expectation) {
        StubMapping stubMapping = givenThat(post("/api/reactions.add")
                .withName(expectation.description())
                .withFormParam("name", equalTo(expectation.reaction()))
                .withFormParam("channel", equalTo(expectation.channelId()))
                .withFormParam("timestamp", equalTo(expectation.ts().toString()))
                .willReturn(okJson("""
                {"ok":true}
                """)));
        return Stub.builder()
                .mapping(stubMapping)
                .wireMockServer(this)
                .description(expectation.description())
                .build();
    }

    public Stub stubReactionRemove(ReactionAddedExpectation expectation) {
        StubMapping stubMapping = givenThat(post("/api/reactions.remove")
                .withName(expectation.description())
                .withFormParam("name", equalTo(expectation.reaction()))
                .withFormParam("channel", equalTo(expectation.channelId()))
                .withFormParam("timestamp", equalTo(expectation.ts().toString()))
                .willReturn(okJson("""
                {"ok":true}
                """)));
        return Stub.builder()
                .mapping(stubMapping)
                .wireMockServer(this)
                .description(expectation.description())
                .build();
    }

    public <T> StubWithResult<T> stubMessagePosted(ThreadMessagePostedExpectation<T> expectation) {
        StubMapping mapping = givenThat(expectation
                .receiver()
                .configureStub(post("/api/chat.postMessage"))
                .withName(expectation.description())
                .withFormParam("channel", equalTo(expectation.channelId()))
                .withFormParam("thread_ts", equalTo(expectation.threadTs().toString()))
                .willReturn(aResponse()
                        .withTransformers("response-template")
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withTransformerParameter("newTs", expectation.newMessageTs())
                        .withBody("""
                    {{formData request.body 'formArgs' urlDecode=true}}
                    {
                      "ok": true,
                      "channel": "{{formArgs.channel}}",
                      "ts": "{{parameters.newTs}}",
                      "message": {
                        "user": "UNSET_BY_TESTS",
                        "parent_user_id": "UNSET_BY_TESTS",
                        "bot_id": "UNSET_BY_TESTS",
                        "app_id": "UNSET_BY_TESTS",
                        "team": "UNSET_BY_TESTS",
                        "type": "message",
                        "ts": "{{parameters.newTs}}",
                        "thread_ts": "{{formArgs.thread_ts}}",
                        "text": "{{formArgs.text}}",
                        "attachments": {{formArgs.attachments}},
                        "blocks": {{formArgs.blocks}}
                      }
                    }
                    """)));
        return StubWithResult.<T>builder()
                .mapping(mapping)
                .wireMockServer(this)
                .receiver(expectation.receiver())
                .description(expectation.description())
                .build();
    }

    public <T> StubWithResult<T> stubMessageUpdated(MessageUpdatedExpectation<T> expectation) {
        StubMapping stubMapping = givenThat(expectation
                .receiver()
                .configureStub(post("/api/chat.update"))
                .withName(expectation.description())
                .withFormParam("channel", equalTo(expectation.channelId()))
                .withFormParam("ts", equalTo(expectation.ts().toString()))
                .willReturn(aResponse()
                        .withTransformers("response-template")
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(StringSubstitutor.replace(
                                """
                    {{formData request.body 'formArgs' urlDecode=true}}
                    {
                      "ok": true,
                      "channel": "{{formArgs.channel}}",
                      "ts": "{{formArgs.ts}}",
                      "message": {
                        "user": "UNSET_BY_TESTS",
                        "edited": {
                          "user": "UNSET_BY_TESTS",
                          "ts": "UNSET_BY_TESTS"
                        },
                        "bot_id": "UNSET_BY_TESTS",
                        "app_id": "UNSET_BY_TESTS",
                        "team": "UNSET_BY_TESTS",
                        "type": "message",
                        "ts": "{{formArgs.ts}}",
                        "thread_ts": "${thread_ts}",
                        "attachments": {{formArgs.attachments}},
                        "blocks": {{formArgs.blocks}}
                      }
                    }
                    """, Map.of("thread_ts", expectation.threadTs().toString())))));
        return StubWithResult.<T>builder()
                .mapping(stubMapping)
                .wireMockServer(this)
                .receiver(expectation.receiver())
                .description(expectation.description())
                .build();
    }

    public Stub stubGetPermalink(String description, String channelId, MessageTs ts, String permalink) {
        StubMapping stubMapping = givenThat(post("/api/chat.getPermalink")
                .withName(description)
                .withFormParam("channel", equalTo(channelId))
                .withFormParam("message_ts", equalTo(ts.toString()))
                .willReturn(okJson(StringSubstitutor.replace(
                        """
                {
                  "ok": true,
                  "channel": "${channelId}",
                  "permalink": "${permalink}"
                }
                """,
                        Map.of(
                                "channelId", channelId,
                                "permalink", permalink)))));
        return Stub.builder()
                .mapping(stubMapping)
                .wireMockServer(this)
                .description(description)
                .build();
    }

    public Stub stubGetPermalink(String description, String channelId, MessageTs ts) {
        return stubGetPermalink(description, channelId, ts, "https://slack.com/messages/" + channelId + "/" + ts);
    }

    public Stub stubGetMessage(MessageToGet message) {
        String responseJson = StringSubstitutor.replace(
                """
            {
                "ok": true,
                "oldest": "${ts}",
                "messages": [
                    {
                        "text": "${text}",
                        "user": ${userId},
                        "bot_id": ${botId},
                        "team": "${team}",
                        "type": "message",
                        "ts": "${ts}",
                        "thread_ts": "${threadTs}",
                        "blocks": ${blocks}
                    }
                ]
            }
            """,
                Map.of(
                        "userId", message.userId() != null ? "\"" + message.userId() + "\"" : "null",
                        "botId", message.botId() != null ? "\"" + message.botId() + "\"" : "null",
                        "text", message.text().replace("\"", "\\\""),
                        "team", config.team(),
                        "blocks", message.blocksJson(),
                        "ts", message.ts(),
                        "threadTs", message.threadTs()));
        StubMapping stubMapping = givenThat(post("/api/conversations.history")
                .withName(message.description())
                .withFormParam("channel", equalTo(message.channelId()))
                .withFormParam("limit", equalTo("1"))
                .withFormParam("oldest", equalTo(message.ts().toString()))
                .withFormParam("inclusive", equalTo("1"))
                .withFormParam("include_all_metadata", equalTo("0"))
                .willReturn(okJson(responseJson)));
        return Stub.builder()
                .mapping(stubMapping)
                .wireMockServer(this)
                .description(message.description())
                .build();
    }

    /**
     * Stub the conversations.replies API to return message info with thread context.
     * This is used to check if a message is a thread reply.
     */
    public Stub stubConversationsReplies(ConversationRepliesToGet conversationReplies) {
        String messages;
        // Slack doesn't set thread_ts in case it's a single message in the thread,
        // But in case there are multiple messages, it sets it for all of them,
        if (conversationReplies.reply() != null) {
            assertThat(conversationReplies.threadTs()).isNotNull();
            messages = StringSubstitutor.replace(
                    """
                [
                    {
                        "type": "message",
                        "ts": "${ts}",
                        "thread_ts": ${threadTsValue}
                    },
                    {
                        "type": "message",
                        "ts": "${replyTs}",
                        "thread_ts": ${threadTsValue}
                    }
                ]
                """,
                    Map.of(
                            "ts", conversationReplies.ts().toString(),
                            "threadTsValue", conversationReplies.threadTs().toString(),
                            "replyTs", conversationReplies.reply().toString()));
        } else {
            messages = StringSubstitutor.replace(
                    """
                [
                    {
                        "type": "message",
                        "ts": "${ts}",
                        "thread_ts": null
                    }
                ]
                """, Map.of("ts", conversationReplies.ts().toString()));
        }
        StubMapping stubMapping = givenThat(post("/api/conversations.replies")
                .withName(conversationReplies.description())
                .withFormParam("channel", equalTo(conversationReplies.channelId()))
                .withFormParam("ts", equalTo(conversationReplies.ts().toString()))
                .withFormParam("limit", equalTo("1"))
                .willReturn(okJson(StringSubstitutor.replace("""
                    {
                        "ok": true,
                        "messages": ${messages}
                    }
                    """, Map.of("messages", messages)))));
        return Stub.builder()
                .mapping(stubMapping)
                .wireMockServer(this)
                .description(conversationReplies.description())
                .build();
    }

    public Stub stubGetUserProfileById(UserProfileToGet userProfile) {
        StubMapping stubMapping = givenThat(post("/api/users.info")
                .withName(userProfile.description())
                .withFormParam("user", equalTo(userProfile.userId()))
                .willReturn(okJson(StringSubstitutor.replace(
                        """
                {
                    "ok": true,
                    "user": {
                        "id": "${userId}",
                        "is_bot": false,
                        "profile": {
                            "email": "${email}"
                        }
                    }
                }
                """,
                        Map.of(
                                "userId", userProfile.userId(),
                                "email", userProfile.email())))));
        return Stub.builder()
                .mapping(stubMapping)
                .wireMockServer(this)
                .description(userProfile.description())
                .build();
    }

    /**
     * Permanent fallback for users.info calls used by team suggestions.
     * Tests can still override with a more specific users.info stub as needed.
     */
    public void stubUsersInfoDefault(String description) {
        givenThat(post("/api/users.info").withName(description).willReturn(okJson("""
                        {
                          "ok": true,
                          "user": {
                            "id": "UNSET_BY_TESTS",
                            "is_bot": false,
                            "profile": {
                              "email": "functional-user@example.com"
                            }
                          }
                        }
                        """)));
    }

    public <T> StubWithResult<T> stubEphemeralMessagePosted(EphemeralMessageExpectation<T> expectation) {
        StubMapping stubMapping = givenThat(expectation
                .receiver()
                .configureStub(post("/api/chat.postEphemeral"))
                .withName(expectation.description())
                .withFormParam("channel", equalTo(expectation.channelId()))
                .withFormParam("thread_ts", equalTo(expectation.threadTs().toString()))
                .withFormParam("user", equalTo(expectation.userId()))
                .willReturn(okJson("""
                {"ok": true, "message_ts": "1234567890.123456"}
                """)));
        return StubWithResult.<T>builder()
                .mapping(stubMapping)
                .wireMockServer(this)
                .receiver(expectation.receiver())
                .description(expectation.description())
                .build();
    }

    public <T> StubWithResult<T> stubViewsOpen(ViewsOpenExpectation<T> expectation) {
        StubMapping mapping = givenThat(expectation
                .receiver()
                .configureStub(post("/api/views.open"))
                .withName(expectation.description())
                .withFormParam("trigger_id", equalTo(expectation.triggerId()))
                .willReturn(aResponse()
                        .withTransformers("response-template")
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                    {{formData request.body 'formArgs' urlDecode=true}}
                    {{parseJson formArgs.view 'formArgView'}}
                    {
                      "ok": true,
                      "view": {
                        "id":"UNSET_BY_TESTS",
                        "team_id":"UNSET_BY_TESTS",
                        "root_view_id":"UNSET_BY_TESTS",
                        "app_id":"UNSET_BY_TESTS",
                        "external_id":"",
                        "app_installed_team_id":"UNSET_BY_TESTS",
                        "bot_id":"UNSET_BY_TESTS",
                        "hash":"UNSET_BY_TESTS",
                        "type":"modal",
                        "callback_id": {{toJson formArgView.callback_id}},
                        "blocks": {{toJson formArgView.blocks}},
                        "private_metadata": {{toJson formArgView.private_metadata}},
                        "title": {{toJson formArgView.title}},
                        "close": {{toJson formArgView.close}},
                        "submit": {{toJson formArgView.submit}}
                      }
                    }
                    """)));
        return StubWithResult.<T>builder()
                .mapping(mapping)
                .wireMockServer(this)
                .receiver(expectation.receiver())
                .description(expectation.description())
                .build();
    }

    /**
     * Returns a DSL for setting up permanent stubs for NFT.
     * Permanent stubs use response templating and are not cleaned up between iterations.
     */
    public PermanentStubs permanent() {
        return new PermanentStubs();
    }

    /**
     * Inner class providing DSL for permanent NFT stubs.
     * These stubs use response templating to echo back IDs and add realistic latency.
     * Latency values are based on actual Slack API measurements.
     */
    public class PermanentStubs {
        /**
         * Stub views.open for summary modal.
         * Uses response templating to echo back view content.
         * Latency: ~100ms (based on actual Slack API measurements).
         */
        public void stubViewsOpen() {
            givenThat(post("/api/views.open")
                    .withName("permanent-views-open")
                    .willReturn(aResponse()
                            .withTransformers("response-template")
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withUniformRandomDelay(80, 120)
                            .withBody("""
                        {{formData request.body 'formArgs' urlDecode=true}}
                        {{parseJson formArgs.view 'formArgView'}}
                        {
                          "ok": true,
                          "view": {
                            "id": "V{{randomInt lower=1000000000 upper=9999999999}}",
                            "team_id": "UNSET_BY_TESTS",
                            "root_view_id": "UNSET_BY_TESTS",
                            "app_id": "UNSET_BY_TESTS",
                            "external_id": "",
                            "app_installed_team_id": "UNSET_BY_TESTS",
                            "bot_id": "UNSET_BY_TESTS",
                            "hash": "UNSET_BY_TESTS",
                            "type": "modal",
                            "callback_id": {{toJson formArgView.callback_id}},
                            "blocks": {{toJson formArgView.blocks}},
                            "private_metadata": {{toJson formArgView.private_metadata}},
                            "title": {{toJson formArgView.title}},
                            "close": {{toJson formArgView.close}},
                            "submit": {{toJson formArgView.submit}}
                          }
                        }
                        """)));
            LOGGER.info("Set up permanent stub for views.open");
        }

        /**
         * Stub reactions.add for checkmark.
         * Latency: ~125ms (based on actual Slack API measurements).
         */
        public void stubReactionAdd() {
            givenThat(post("/api/reactions.add")
                    .withName("permanent-reaction-add")
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withUniformRandomDelay(100, 150)
                            .withBody("""
                        {"ok": true}
                        """)));
            LOGGER.info("Set up permanent stub for reactions.add");
        }

        /**
         * Stub chat.getPermalink.
         * Latency: ~65ms (based on actual Slack API measurements).
         */
        public void stubGetPermalink() {
            givenThat(post("/api/chat.getPermalink")
                    .withName("permanent-get-permalink")
                    .willReturn(aResponse()
                            .withTransformers("response-template")
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withUniformRandomDelay(50, 80)
                            .withBody("""
                        {{formData request.body 'formArgs' urlDecode=true}}
                        {
                          "ok": true,
                          "channel": "{{formArgs.channel}}",
                          "permalink": "https://slack.com/messages/{{formArgs.channel}}/{{formArgs.message_ts}}"
                        }
                        """)));
            LOGGER.info("Set up permanent stub for chat.getPermalink");
        }

        /**
         * Stub conversations.history for message fetch.
         * Latency: ~125ms (based on actual Slack API measurements).
         */
        public void stubGetMessage() {
            givenThat(post("/api/conversations.history")
                    .withName("permanent-get-message")
                    .willReturn(aResponse()
                            .withTransformers("response-template")
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withUniformRandomDelay(100, 150)
                            .withBody(StringSubstitutor.replace("""
                        {{formData request.body 'formArgs' urlDecode=true}}
                        {
                          "ok": true,
                          "oldest": "{{formArgs.oldest}}",
                          "messages": [
                            {
                              "text": "NFT test message",
                              "user": "UNFT_USER",
                              "team": "${team}",
                              "type": "message",
                              "ts": "{{formArgs.oldest}}",
                              "thread_ts": "{{formArgs.oldest}}",
                              "blocks": []
                            }
                          ]
                        }
                        """, Map.of("team", config.team())))));
            LOGGER.info("Set up permanent stub for conversations.history");
        }

        /**
         * Stub conversations.replies for thread context.
         * Latency: ~125ms (similar to conversations.history).
         */
        public void stubConversationsReplies() {
            givenThat(post("/api/conversations.replies")
                    .withName("permanent-conversations-replies")
                    .willReturn(aResponse()
                            .withTransformers("response-template")
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withUniformRandomDelay(100, 150)
                            .withBody("""
                        {{formData request.body 'formArgs' urlDecode=true}}
                        {
                          "ok": true,
                          "messages": [
                            {
                              "type": "message",
                              "ts": "{{formArgs.ts}}",
                              "thread_ts": null
                            }
                          ]
                        }
                        """)));
            LOGGER.info("Set up permanent stub for conversations.replies");
        }

        /**
         * Stub chat.postEphemeral for rating request.
         * Latency: ~160ms (based on actual Slack API measurements).
         */
        public void stubEphemeralMessage() {
            givenThat(post("/api/chat.postEphemeral")
                    .withName("permanent-ephemeral-message")
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withUniformRandomDelay(120, 200)
                            .withBody("""
                        {"ok": true, "message_ts": "1234567890.123456"}
                        """)));
            LOGGER.info("Set up permanent stub for chat.postEphemeral");
        }

        /**
         * Stub users.info for user profile lookup.
         * Latency: ~100ms (estimate similar to views.open).
         */
        public void stubUsersInfo() {
            givenThat(post("/api/users.info")
                    .withName("permanent-users-info")
                    .willReturn(aResponse()
                            .withTransformers("response-template")
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withUniformRandomDelay(40, 60)
                            .withBody("""
                        {{formData request.body 'formArgs' urlDecode=true}}
                        {
                          "ok": true,
                          "user": {
                            "id": "{{formArgs.user}}",
                            "is_bot": false,
                            "profile": {
                              "email": "nft-user@example.com"
                            }
                          }
                        }
                        """)));
            LOGGER.info("Set up permanent stub for users.info");
        }

        /**
         * Sets up all permanent stubs needed for NFT.
         */
        public void setupAllNftStubs() {
            stubAuthTest("nft-auth-test");
            stubViewsOpen();
            stubReactionAdd();
            stubGetPermalink();
            stubGetMessage();
            stubConversationsReplies();
            stubEphemeralMessage();
            stubUsersInfo();
            LOGGER.info("Set up all permanent NFT stubs");
        }
    }
}
