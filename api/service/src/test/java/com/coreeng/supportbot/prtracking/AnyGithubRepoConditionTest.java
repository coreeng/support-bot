package com.coreeng.supportbot.prtracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;

class AnyGithubRepoConditionTest {

    private final AnyGithubRepoCondition condition = new AnyGithubRepoCondition();

    @Test
    void matchesWhenAtLeastOneRepoIsExplicitGitHub() {
        Environment env = environmentWith(repo(0, "org/repo", "github"));

        assertThat(condition.matches(contextFor(env), mock(org.springframework.core.type.AnnotatedTypeMetadata.class)))
                .isTrue();
    }

    @Test
    void matchesWhenAtLeastOneRepoOmitsProvider() {
        // Omitted provider defaults to github — covers the most common deployment shape and the
        // pre-GitLab "no provider field at all" case.
        Environment env = environmentWith(repoWithoutProvider(0, "org/repo"));

        assertThat(condition.matches(contextFor(env), mock(org.springframework.core.type.AnnotatedTypeMetadata.class)))
                .isTrue();
    }

    @Test
    void doesNotMatchWhenAllReposAreGitLab() {
        Environment env = environmentWith(repo(0, "my-group/project", "gitlab"), repo(1, "my-group/another", "gitlab"));

        assertThat(condition.matches(contextFor(env), mock(org.springframework.core.type.AnnotatedTypeMetadata.class)))
                .isFalse();
    }

    @Test
    void matchesMixedConfigurationWithBothProviders() {
        Environment env = environmentWith(repo(0, "my-group/project", "gitlab"), repo(1, "org/repo", "github"));

        assertThat(condition.matches(contextFor(env), mock(org.springframework.core.type.AnnotatedTypeMetadata.class)))
                .isTrue();
    }

    @Test
    void matchProviderCaseInsensitively() {
        Environment env = environmentWith(repo(0, "org/repo", "GitHub"));

        assertThat(condition.matches(contextFor(env), mock(org.springframework.core.type.AnnotatedTypeMetadata.class)))
                .isTrue();
    }

    @Test
    void doesNotMatchWhenNoReposConfigured() {
        // Bot won't reach a tickable state without repos anyway — defer to PrTrackingProps'
        // "repositories must not be empty" check. The condition itself just answers "false".
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
        // Past the last configured index — the condition stops scanning here.
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
