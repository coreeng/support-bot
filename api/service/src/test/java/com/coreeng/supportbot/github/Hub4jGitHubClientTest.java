package com.coreeng.supportbot.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.*;

class Hub4jGitHubClientTest {

    private final GitHub gitHub = mock(GitHub.class);
    private final Hub4jGitHubClient client = new Hub4jGitHubClient(gitHub);

    @Test
    void returnsPullRequestOnHappyPath() throws IOException {
        // given
        GHRepository repo = mock(GHRepository.class);
        GHPullRequest pr = new GHPullRequest();
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        when(gitHub.getRepository("my-org/my-repo")).thenReturn(repo);
        when(repo.getPullRequest(42)).thenReturn(pr);
        setCreatedAtRaw(pr, "2026-01-01T00:00:00Z");
        setStateRaw(pr, "open");

        // when
        GitHubPullRequest result = client.getPullRequest("my-org/my-repo", 42);

        // then
        assertThat(result.repositoryName()).isEqualTo("my-org/my-repo");
        assertThat(result.pullRequestNumber()).isEqualTo(42);
        assertThat(result.createdAt()).isEqualTo(createdAt);
        assertThat(result.state()).isEqualTo(GitHubPullRequest.PrState.OPEN);
    }

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
                .satisfies(
                        ex -> assertThat(((GitHubApiException) ex).statusCode()).isEqualTo(0))
                .hasMessageContaining("null created_at");
    }

    @Test
    void wrapsNullStateAsGitHubApiException() throws IOException {
        // given
        GHRepository repo = mock(GHRepository.class);
        GHPullRequest pr = new GHPullRequest();
        when(gitHub.getRepository("my-org/my-repo")).thenReturn(repo);
        when(repo.getPullRequest(42)).thenReturn(pr);
        setCreatedAtRaw(pr, "2026-01-01T00:00:00Z");
        setStateRaw(pr, null);

        // when / then
        assertThatThrownBy(() -> client.getPullRequest("my-org/my-repo", 42))
                .isInstanceOf(GitHubApiException.class)
                .satisfies(
                        ex -> assertThat(((GitHubApiException) ex).statusCode()).isEqualTo(0))
                .hasMessageContaining("null state");
    }

    @Test
    void wrapsNotFoundAsGitHubApiException() throws IOException {
        // given
        when(gitHub.getRepository("my-org/my-repo")).thenThrow(new GHFileNotFoundException("Not Found"));

        // when / then
        assertThatThrownBy(() -> client.getPullRequest("my-org/my-repo", 999))
                .isInstanceOf(GitHubApiException.class)
                .satisfies(
                        ex -> assertThat(((GitHubApiException) ex).statusCode()).isEqualTo(404))
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
                .satisfies(
                        ex -> assertThat(((GitHubApiException) ex).statusCode()).isEqualTo(401))
                .hasMessageContaining("401");
    }

    @Test
    void wrapsIOExceptionAsGitHubApiException() throws IOException {
        // given
        when(gitHub.getRepository("my-org/my-repo")).thenThrow(new IOException("Connection refused"));

        // when / then
        assertThatThrownBy(() -> client.getPullRequest("my-org/my-repo", 1))
                .isInstanceOf(GitHubApiException.class)
                .satisfies(
                        ex -> assertThat(((GitHubApiException) ex).statusCode()).isEqualTo(0))
                .hasMessageContaining("my-org/my-repo#1");
    }

    private static void setCreatedAtRaw(GHPullRequest pr, String createdAtRaw) {
        setFieldOnClassHierarchy(pr, "createdAt", createdAtRaw);
    }

    private static void setStateRaw(GHPullRequest pr, @Nullable String stateRaw) {
        setFieldOnClassHierarchy(pr, "state", stateRaw);
    }

    private static void setFieldOnClassHierarchy(Object target, String fieldName, @Nullable Object value) {
        try {
            Class<?> type = target.getClass();
            Field field = null;
            while (type != null && field == null) {
                try {
                    field = type.getDeclaredField(fieldName);
                } catch (NoSuchFieldException ignored) {
                    type = type.getSuperclass();
                }
            }
            if (field == null) {
                throw new NoSuchFieldException(fieldName);
            }
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new LinkageError("Failed to set %s on GHPullRequest test object".formatted(fieldName), e);
        }
    }
}
