package com.coreeng.supportbot.github;

import org.junit.jupiter.api.Test;
import org.kohsuke.github.*;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Hub4jGitHubClientTest {

    private final GitHub gitHub = mock(GitHub.class);
    private final Hub4jGitHubClient client = new Hub4jGitHubClient(gitHub);

    @Test
    void wrapsNullCreatedAtAsGitHubApiException() throws IOException {
        // given
        GHRepository repo = mock(GHRepository.class);
        GHPullRequest pr = mock(GHPullRequest.class); // getCreatedAt() returns null by default
        when(gitHub.getRepository("my-org/my-repo")).thenReturn(repo);
        when(repo.getPullRequest(42)).thenReturn(pr);

        // when / then
        assertThatThrownBy(() -> client.getPullRequest("my-org/my-repo", 42))
                .isInstanceOf(GitHubApiException.class)
                .satisfies(ex -> assertThat(((GitHubApiException) ex).statusCode()).isEqualTo(0))
                .hasMessageContaining("null created_at");
    }

    @Test
    void wrapsNotFoundAsGitHubApiException() throws IOException {
        // given
        when(gitHub.getRepository("my-org/my-repo"))
                .thenThrow(new GHFileNotFoundException("Not Found"));

        // when / then
        assertThatThrownBy(() -> client.getPullRequest("my-org/my-repo", 999))
                .isInstanceOf(GitHubApiException.class)
                .satisfies(ex -> assertThat(((GitHubApiException) ex).statusCode()).isEqualTo(404))
                .hasMessageContaining("my-org/my-repo#999");
    }

    @Test
    void wrapsHttpErrorAsGitHubApiException() throws IOException {
        // given
        when(gitHub.getRepository("my-org/my-repo"))
                .thenThrow(new HttpException(401, "Unauthorized", (String) null, null));

        // when / then
        assertThatThrownBy(() -> client.getPullRequest("my-org/my-repo", 1))
                .isInstanceOf(GitHubApiException.class)
                .satisfies(ex -> assertThat(((GitHubApiException) ex).statusCode()).isEqualTo(401))
                .hasMessageContaining("401");
    }

    @Test
    void wrapsIOExceptionAsGitHubApiException() throws IOException {
        // given
        when(gitHub.getRepository("my-org/my-repo")).thenThrow(new IOException("Connection refused"));

        // when / then
        assertThatThrownBy(() -> client.getPullRequest("my-org/my-repo", 1))
                .isInstanceOf(GitHubApiException.class)
                .satisfies(ex -> assertThat(((GitHubApiException) ex).statusCode()).isEqualTo(0))
                .hasMessageContaining("my-org/my-repo#1");
    }
}
