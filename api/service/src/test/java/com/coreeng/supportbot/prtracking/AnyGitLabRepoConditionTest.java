package com.coreeng.supportbot.prtracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;

class AnyGitLabRepoConditionTest {

    private final AnyGitLabRepoCondition condition = new AnyGitLabRepoCondition();

    @Test
    void matchesWhenAtLeastOneRepoIsExplicitGitLab() {
        Environment env = environmentWith(repo(0, "my-group/project", "gitlab"));

        assertThat(condition.matches(contextFor(env), mock(org.springframework.core.type.AnnotatedTypeMetadata.class)))
                .isTrue();
    }

    @Test
    void doesNotMatchWhenAllReposAreGitHub() {
        // Omitted provider defaults to github; explicit "github" must not match the gitlab gate.
        Environment env = environmentWith(repo(0, "org/repo", "github"), repoWithoutProvider(1, "org/another"));

        assertThat(condition.matches(contextFor(env), mock(org.springframework.core.type.AnnotatedTypeMetadata.class)))
                .isFalse();
    }

    @Test
    void matchesMixedConfigurationWithBothProviders() {
        Environment env = environmentWith(repo(0, "org/repo", "github"), repo(1, "my-group/project", "gitlab"));

        assertThat(condition.matches(contextFor(env), mock(org.springframework.core.type.AnnotatedTypeMetadata.class)))
                .isTrue();
    }

    @Test
    void matchProviderCaseInsensitively() {
        Environment env = environmentWith(repo(0, "my-group/project", "GitLab"));

        assertThat(condition.matches(contextFor(env), mock(org.springframework.core.type.AnnotatedTypeMetadata.class)))
                .isTrue();
    }

    @Test
    void doesNotMatchWhenNoReposConfigured() {
        Environment env = mock(Environment.class);
        when(env.containsProperty("pr-review-tracking.repositories[0].name")).thenReturn(false);

        assertThat(condition.matches(contextFor(env), mock(org.springframework.core.type.AnnotatedTypeMetadata.class)))
                .isFalse();
    }

    private static ConditionContext contextFor(Environment env) {
        ConditionContext ctx = mock(ConditionContext.class);
        when(ctx.getEnvironment()).thenReturn(env);
        return ctx;
    }

    private static Environment environmentWith(RepoStub... repos) {
        Environment env = mock(Environment.class);
        for (RepoStub r : repos) {
            String namePath = "pr-review-tracking.repositories[" + r.index() + "].name";
            String providerPath = "pr-review-tracking.repositories[" + r.index() + "].provider";
            when(env.containsProperty(namePath)).thenReturn(true);
            when(env.getProperty(providerPath, "github")).thenReturn(r.provider() == null ? "github" : r.provider());
        }
        when(env.containsProperty("pr-review-tracking.repositories[" + repos.length + "].name"))
                .thenReturn(false);
        return env;
    }

    private static RepoStub repo(int index, String name, String provider) {
        return new RepoStub(index, name, provider);
    }

    private static RepoStub repoWithoutProvider(int index, String name) {
        return new RepoStub(index, name, null);
    }

    private record RepoStub(
            int index, String name, @Nullable String provider) {}
}
