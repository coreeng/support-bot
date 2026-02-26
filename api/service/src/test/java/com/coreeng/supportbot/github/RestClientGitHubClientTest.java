package com.coreeng.supportbot.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestClientGitHubClientTest {

    @Test
    void fetchesPullWithBearerTokenHeader() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.github.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        RestClientGitHubClient client = new RestClientGitHubClient(restClient, () -> "token-123");

        server.expect(requestTo("https://api.github.com/repos/my-org/my-repo/pulls/42"))
                .andExpect(method(GET))
                .andExpect(header("Authorization", "Bearer token-123"))
                .andRespond(withSuccess(
                        "{\"state\":\"open\",\"created_at\":\"2026-02-26T10:00:00Z\"}", MediaType.APPLICATION_JSON));

        GitHubPullRequest pull = client.getPullRequest("my-org/my-repo", 42);
        assertThat(pull.repositoryName()).isEqualTo("my-org/my-repo");
        assertThat(pull.pullRequestNumber()).isEqualTo(42);
        assertThat(pull.state()).isEqualTo("open");
        assertThat(pull.createdAt()).isEqualTo(Instant.parse("2026-02-26T10:00:00Z"));
        server.verify();
    }

    @Test
    void rejectsInvalidRepositoryFormat() {
        RestClientGitHubClient client =
                new RestClientGitHubClient(RestClient.builder().baseUrl("https://api.github.com").build(), () -> "x");

        assertThatThrownBy(() -> client.getPullRequest("invalid-repo", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("org/repo");
    }

    @Test
    void throwsGitHubApiExceptionOnNotFound() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.github.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClientGitHubClient client = new RestClientGitHubClient(builder.build(), () -> "token");

        server.expect(requestTo("https://api.github.com/repos/my-org/my-repo/pulls/999"))
                .andRespond(withResourceNotFound());

        assertThatThrownBy(() -> client.getPullRequest("my-org/my-repo", 999))
                .isInstanceOf(GitHubApiException.class)
                .satisfies(ex -> assertThat(((GitHubApiException) ex).statusCode()).isEqualTo(404))
                .hasMessageContaining("my-org/my-repo#999");
        server.verify();
    }

    @Test
    void throwsGitHubApiExceptionOnUnauthorized() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.github.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClientGitHubClient client = new RestClientGitHubClient(builder.build(), () -> "bad-token");

        server.expect(requestTo("https://api.github.com/repos/my-org/my-repo/pulls/1"))
                .andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> client.getPullRequest("my-org/my-repo", 1))
                .isInstanceOf(GitHubApiException.class)
                .satisfies(ex -> assertThat(((GitHubApiException) ex).statusCode()).isEqualTo(401))
                .hasMessageContaining("401");
        server.verify();
    }

    @Test
    void throwsGitHubApiExceptionOnServerError() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.github.com");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClientGitHubClient client = new RestClientGitHubClient(builder.build(), () -> "token");

        server.expect(requestTo("https://api.github.com/repos/my-org/my-repo/pulls/1"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.getPullRequest("my-org/my-repo", 1))
                .isInstanceOf(GitHubApiException.class)
                .satisfies(ex -> assertThat(((GitHubApiException) ex).statusCode()).isEqualTo(500))
                .hasMessageContaining("500");
        server.verify();
    }
}
