package com.coreeng.supportbot.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.Test;

import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHIssueState;
import org.kohsuke.github.GHPullRequest;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.HttpException;

class Hub4jGitHubClientTest {

    private final GitHub gitHub = mock(GitHub.class);
    private final Hub4jGitHubClient client = new Hub4jGitHubClient(gitHub);

    @Test
    void returnsPullRequestMetadata() throws IOException {
        Instant createdAt = Instant.parse("2026-02-26T10:00:00Z");
        GHRepository repo = mock(GHRepository.class);
        GHPullRequest pr = mock(GHPullRequest.class);

        when(gitHub.getRepository("my-org/my-repo")).thenReturn(repo);
        when(repo.getPullRequest(42)).thenReturn(pr);
        doReturn(Date.from(createdAt)).when(pr).getCreatedAt();
        doReturn(GHIssueState.OPEN).when(pr).getState();

        GitHubPullRequest result = client.getPullRequest("my-org/my-repo", 42);

        assertThat(result.repositoryName()).isEqualTo("my-org/my-repo");
        assertThat(result.pullRequestNumber()).isEqualTo(42);
        assertThat(result.createdAt()).isEqualTo(createdAt);
        assertThat(result.state()).isEqualTo("open");
    }

    @Test
    void wrapsNotFoundAsGitHubApiException() throws IOException {
        when(gitHub.getRepository("my-org/my-repo"))
                .thenThrow(new GHFileNotFoundException("Not Found"));

        assertThatThrownBy(() -> client.getPullRequest("my-org/my-repo", 999))
                .isInstanceOf(GitHubApiException.class)
                .satisfies(ex -> assertThat(((GitHubApiException) ex).statusCode()).isEqualTo(404))
                .hasMessageContaining("my-org/my-repo#999");
    }

    @Test
    void wrapsHttpErrorAsGitHubApiException() throws IOException {
        when(gitHub.getRepository("my-org/my-repo"))
                .thenThrow(new HttpException(401, "Unauthorized", (String) null, null));

        assertThatThrownBy(() -> client.getPullRequest("my-org/my-repo", 1))
                .isInstanceOf(GitHubApiException.class)
                .satisfies(ex -> assertThat(((GitHubApiException) ex).statusCode()).isEqualTo(401))
                .hasMessageContaining("401");
    }

    @Test
    void wrapsIOExceptionAsGitHubApiException() throws IOException {
        when(gitHub.getRepository("my-org/my-repo")).thenThrow(new IOException("Connection refused"));

        assertThatThrownBy(() -> client.getPullRequest("my-org/my-repo", 1))
                .isInstanceOf(GitHubApiException.class)
                .satisfies(ex -> assertThat(((GitHubApiException) ex).statusCode()).isEqualTo(0))
                .hasMessageContaining("my-org/my-repo#1");
    }
}
