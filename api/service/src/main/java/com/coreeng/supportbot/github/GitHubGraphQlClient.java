package com.coreeng.supportbot.github;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Reads GitHub's computed code-owner review state for a PR via the GraphQL API — the REST/hub4j path
 * has no equivalent. Returns {@code reviewDecision} (when branch protection requires code-owner review,
 * {@code APPROVED} means every required code owner has approved) plus the still-pending code-owner
 * reviewers ({@code asCodeOwner == true}) for the chase message.
 *
 * <p>Never throws: any failure — transport error, GraphQL {@code errors}, or missing data — yields
 * {@code null} so the lifecycle degrades to "code owners not yet satisfied" rather than breaking the
 * poll. GitHub already does the CODEOWNERS path-matching; we only read the result.
 */
public class GitHubGraphQlClient {

    private static final Logger LOG = LoggerFactory.getLogger(GitHubGraphQlClient.class);

    private static final String QUERY = """
            query($owner: String!, $repo: String!, $number: Int!) {
              repository(owner: $owner, name: $repo) {
                pullRequest(number: $number) {
                  reviewDecision
                  reviewRequests(first: 100) {
                    nodes {
                      asCodeOwner
                      requestedReviewer {
                        ... on User { login url }
                        ... on Team { combinedSlug url }
                      }
                    }
                  }
                }
              }
            }""";

    private final RestClient restClient;

    public GitHubGraphQlClient(RestClient gitHubGraphQlRestClient) {
        this.restClient = gitHubGraphQlRestClient;
    }

    public @Nullable CodeownerReview fetchCodeownerReview(String repositoryName, int prNumber) {
        int slash = repositoryName.indexOf('/');
        if (slash <= 0 || slash == repositoryName.length() - 1) {
            LOG.atWarn()
                    .addArgument(repositoryName)
                    .log("Invalid GitHub repo name for GraphQL (expected org/repo): {}");
            return null;
        }
        Map<String, Object> body = Map.of(
                "query",
                QUERY,
                "variables",
                Map.of(
                        "owner", repositoryName.substring(0, slash),
                        "repo", repositoryName.substring(slash + 1),
                        "number", prNumber));
        JsonNode root;
        try {
            root = restClient
                    .post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            LOG.atWarn()
                    .addArgument(repositoryName)
                    .addArgument(prNumber)
                    .addArgument(e::getMessage)
                    .log("GitHub GraphQL request failed for {}#{}: {}");
            return null;
        }
        if (root == null) {
            return null;
        }
        // A GraphQL response can carry both partial `data` and a top-level `errors` array: a field-level
        // error (SAML/SSO enforcement, a missing scope, rate-limiting) nulls just that field while the rest
        // of the object resolves. If `reviewDecision` is the field that errored, it arrives null next to a
        // populated `errors` — indistinguishable, downstream, from the legitimate "no code-owner review
        // required" null, which the caller maps to codeOwnersApproved=true (gate open). Treat any errors as
        // a failed read and return null so the gate stays shut and the poll retries, per this class's
        // contract ("any failure ... yields null"). A clean null reviewDecision (no errors) still passes
        // through as "gate doesn't apply".
        JsonNode errors = root.path("errors");
        if (errors.isArray() && !errors.isEmpty()) {
            LOG.atWarn()
                    .addArgument(repositoryName)
                    .addArgument(prNumber)
                    .addArgument(errors::toString)
                    .log("GitHub GraphQL returned errors for {}#{}: {}");
            return null;
        }
        JsonNode pr = root.path("data").path("repository").path("pullRequest");
        if (pr.isMissingNode() || pr.isNull()) {
            LOG.atWarn()
                    .addArgument(repositoryName)
                    .addArgument(prNumber)
                    .addArgument(() -> root.path("errors").toString())
                    .log("GitHub GraphQL returned no pull request for {}#{} (errors: {})");
            return null;
        }
        return new CodeownerReview(parseDecision(pr.path("reviewDecision")), extractCodeOwners(pr));
    }

    private static GitHubPullRequest.@Nullable ReviewDecision parseDecision(JsonNode node) {
        if (!node.isTextual()) {
            return null;
        }
        return switch (node.asText()) {
            case "APPROVED" -> GitHubPullRequest.ReviewDecision.APPROVED;
            case "CHANGES_REQUESTED" -> GitHubPullRequest.ReviewDecision.CHANGES_REQUESTED;
            case "REVIEW_REQUIRED" -> GitHubPullRequest.ReviewDecision.REVIEW_REQUIRED;
            default -> null;
        };
    }

    /** Users / teams GitHub requested because of CODEOWNERS, still pending — the chase list. */
    private static List<CodeOwnerReviewer> extractCodeOwners(JsonNode pr) {
        List<CodeOwnerReviewer> owners = new ArrayList<>();
        for (JsonNode node : pr.path("reviewRequests").path("nodes")) {
            if (!node.path("asCodeOwner").asBoolean(false)) {
                continue;
            }
            JsonNode reviewer = node.path("requestedReviewer");
            if (reviewer.hasNonNull("login")) {
                owners.add(new CodeOwnerReviewer(false, reviewer.get("login").asText(), urlOrNull(reviewer)));
            } else if (reviewer.hasNonNull("combinedSlug")) {
                owners.add(
                        new CodeOwnerReviewer(true, reviewer.get("combinedSlug").asText(), urlOrNull(reviewer)));
            }
        }
        return List.copyOf(owners);
    }

    private static @Nullable String urlOrNull(JsonNode reviewer) {
        return reviewer.hasNonNull("url") ? reviewer.get("url").asText() : null;
    }

    /** Derives the GraphQL endpoint from the REST API base URL (github.com and GitHub Enterprise Server). */
    public static String graphqlEndpoint(String apiBaseUrl) {
        String base = apiBaseUrl.endsWith("/") ? apiBaseUrl.substring(0, apiBaseUrl.length() - 1) : apiBaseUrl;
        if (base.endsWith("/api/v3")) {
            // GitHub Enterprise Server: https://host/api/v3 -> https://host/api/graphql
            return base.substring(0, base.length() - "/api/v3".length()) + "/api/graphql";
        }
        // github.com: https://api.github.com -> https://api.github.com/graphql
        return base + "/graphql";
    }

    /** GitHub's aggregate review verdict for a PR plus the still-pending code-owner reviewers. */
    public record CodeownerReview(
            GitHubPullRequest.@Nullable ReviewDecision reviewDecision, List<CodeOwnerReviewer> codeOwnerReviewers) {
        public CodeownerReview {
            codeOwnerReviewers = List.copyOf(codeOwnerReviewers);
        }
    }
}
