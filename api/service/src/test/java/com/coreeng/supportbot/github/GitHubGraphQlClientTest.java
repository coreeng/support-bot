package com.coreeng.supportbot.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GitHubGraphQlClientTest {

    private static final String GRAPHQL_URL = "https://api.github.com/graphql";

    private MockRestServiceServer server;
    private GitHubGraphQlClient client;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(GRAPHQL_URL)
                .messageConverters(ImmutableList.of(
                        new MappingJackson2HttpMessageConverter(objectMapper),
                        new StringHttpMessageConverter(StandardCharsets.UTF_8)));
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GitHubGraphQlClient(builder.build());
    }

    @Test
    void parsesApprovedDecisionAndCodeOwnerReviewers() {
        server.expect(requestTo(GRAPHQL_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {"data":{"repository":{"pullRequest":{
                          "reviewDecision":"APPROVED",
                          "reviewRequests":{"nodes":[
                            {"asCodeOwner":true,"requestedReviewer":{"login":"alice"}},
                            {"asCodeOwner":true,"requestedReviewer":{"slug":"platform-team"}},
                            {"asCodeOwner":false,"requestedReviewer":{"login":"manual-reviewer"}}
                          ]}
                        }}}}""", MediaType.APPLICATION_JSON));

        GitHubGraphQlClient.CodeownerReview review = client.fetchCodeownerReview("my-org/repo", 42);

        assertThat(review).isNotNull();
        assertThat(review.reviewDecision()).isEqualTo(GitHubPullRequest.ReviewDecision.APPROVED);
        // asCodeOwner=false reviewers are excluded; users (login) and teams (slug) both included.
        assertThat(review.codeOwnerReviewerLogins()).containsExactly("alice", "platform-team");
        server.verify();
    }

    @Test
    void parsesReviewRequiredWithPendingCodeOwners() {
        server.expect(requestTo(GRAPHQL_URL)).andRespond(withSuccess("""
                        {"data":{"repository":{"pullRequest":{
                          "reviewDecision":"REVIEW_REQUIRED",
                          "reviewRequests":{"nodes":[
                            {"asCodeOwner":true,"requestedReviewer":{"login":"carol"}}
                          ]}
                        }}}}""", MediaType.APPLICATION_JSON));

        GitHubGraphQlClient.CodeownerReview review = client.fetchCodeownerReview("my-org/repo", 7);

        assertThat(review).isNotNull();
        assertThat(review.reviewDecision()).isEqualTo(GitHubPullRequest.ReviewDecision.REVIEW_REQUIRED);
        assertThat(review.codeOwnerReviewerLogins()).containsExactly("carol");
    }

    @Test
    void nullReviewDecisionParsesAsNull() {
        server.expect(requestTo(GRAPHQL_URL))
                .andRespond(withSuccess(
                        "{\"data\":{\"repository\":{\"pullRequest\":{\"reviewDecision\":null,\"reviewRequests\":{\"nodes\":[]}}}}}",
                        MediaType.APPLICATION_JSON));

        GitHubGraphQlClient.CodeownerReview review = client.fetchCodeownerReview("my-org/repo", 1);

        assertThat(review).isNotNull();
        assertThat(review.reviewDecision()).isNull();
        assertThat(review.codeOwnerReviewerLogins()).isEmpty();
    }

    @Test
    void missingPullRequestReturnsNull() {
        server.expect(requestTo(GRAPHQL_URL))
                .andRespond(
                        withSuccess("{\"data\":{\"repository\":{\"pullRequest\":null}}}", MediaType.APPLICATION_JSON));

        assertThat(client.fetchCodeownerReview("my-org/repo", 1)).isNull();
    }

    @Test
    void graphQlErrorsReturnNull() {
        server.expect(requestTo(GRAPHQL_URL))
                .andRespond(withSuccess(
                        "{\"data\":null,\"errors\":[{\"message\":\"Could not resolve to a Repository\"}]}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.fetchCodeownerReview("my-org/repo", 1)).isNull();
    }

    @Test
    void transportErrorReturnsNull() {
        server.expect(requestTo(GRAPHQL_URL)).andRespond(withServerError());

        assertThat(client.fetchCodeownerReview("my-org/repo", 1)).isNull();
    }

    @Test
    void invalidRepoNameReturnsNullWithoutRequest() {
        // No server.expect(): a malformed repo name must short-circuit before any HTTP call.
        assertThat(client.fetchCodeownerReview("no-slash", 1)).isNull();
        server.verify();
    }

    @Test
    void derivesGraphqlEndpointForDotComAndEnterprise() {
        assertThat(GitHubGraphQlClient.graphqlEndpoint("https://api.github.com"))
                .isEqualTo("https://api.github.com/graphql");
        assertThat(GitHubGraphQlClient.graphqlEndpoint("https://api.github.com/"))
                .isEqualTo("https://api.github.com/graphql");
        assertThat(GitHubGraphQlClient.graphqlEndpoint("https://ghe.example.com/api/v3"))
                .isEqualTo("https://ghe.example.com/api/graphql");
    }
}
