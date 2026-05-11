package com.coreeng.supportbot.prtracking;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.config.PrTrackingProps;
import com.coreeng.supportbot.prtracking.source.Provider;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class PrUrlResolverTest {

    @Test
    void buildsGitHubUrlInThePullPath() {
        PrUrlResolver resolver = new PrUrlResolver(propsWith(gitHubRepo("my-org/my-repo")));
        assertThat(resolver.publicUrlFor("my-org/my-repo", 42)).isEqualTo("https://github.com/my-org/my-repo/pull/42");
    }

    @Test
    void buildsGitLabUrlWithMergeRequestSeparator() {
        // GitLab MR URLs use /-/merge_requests/ rather than /pull/ — this is the central reason the
        // builder splits per provider rather than templating a single suffix.
        PrUrlResolver resolver = new PrUrlResolver(propsWithGitLab("my-group/project", "https://gitlab.com/api/v4"));
        assertThat(resolver.publicUrlFor("my-group/project", 7))
                .isEqualTo("https://gitlab.com/my-group/project/-/merge_requests/7");
    }

    @Test
    void stripsApiV4SuffixForSelfHostedGitLab() {
        PrUrlResolver resolver = new PrUrlResolver(propsWithGitLab("infra/platform", "https://gitlab.internal/api/v4"));
        assertThat(resolver.publicUrlFor("infra/platform", 1))
                .isEqualTo("https://gitlab.internal/infra/platform/-/merge_requests/1");
    }

    @Test
    void gitLabHostsContainsConfiguredHostsOnly() {
        PrUrlResolver resolver = new PrUrlResolver(new PrTrackingProps(
                true,
                "0 * * * * *",
                "pr",
                List.of("support"),
                "team",
                "days",
                List.of(gitHubRepo("my-org/repo"), gitLabRepo("my-group/project", null)),
                new PrTrackingProps.GitHub(
                        PrTrackingProps.AuthMode.TOKEN, "https://api.github.com", "token", "", "", ""),
                new PrTrackingProps.Gitlab("https://gitlab.com/api/v4", "token"),
                new PrTrackingProps.SlaDiscovery(Duration.ofHours(1))));

        assertThat(resolver.gitLabHosts()).containsExactly("gitlab.com");
    }

    @Test
    void repoUrlOmitsPrSuffix() {
        PrUrlResolver resolver = new PrUrlResolver(propsWithGitLab("my-group/project", "https://gitlab.com/api/v4"));
        assertThat(resolver.repoUrl("my-group/project")).isEqualTo("https://gitlab.com/my-group/project");
    }

    @Test
    void fallsBackToGitHubUrlForUnknownRepo() {
        // A row persisted in pr_tracking but missing from the current config — e.g. operator
        // removed the repo after the PR was detected — must still render a sensible URL for the
        // in-flight dashboard rather than crashing the SQL fetch.
        PrUrlResolver resolver = new PrUrlResolver(propsWith(gitHubRepo("my-org/repo")));
        assertThat(resolver.publicUrlFor("ghost-org/ghost-repo", 99))
                .isEqualTo("https://github.com/ghost-org/ghost-repo/pull/99");
    }

    private static PrTrackingProps propsWith(PrTrackingProps.Repository repo) {
        return new PrTrackingProps(
                true,
                "0 * * * * *",
                "pr",
                List.of("support"),
                "team",
                "days",
                List.of(repo),
                new PrTrackingProps.GitHub(
                        PrTrackingProps.AuthMode.TOKEN, "https://api.github.com", "token", "", "", ""),
                null,
                new PrTrackingProps.SlaDiscovery(Duration.ofHours(1)));
    }

    private static PrTrackingProps propsWithGitLab(String repoName, String globalApiBaseUrl) {
        return new PrTrackingProps(
                true,
                "0 * * * * *",
                "pr",
                List.of("support"),
                "team",
                "days",
                List.of(gitLabRepo(repoName, null)),
                new PrTrackingProps.GitHub(
                        PrTrackingProps.AuthMode.TOKEN, "https://api.github.com", "token", "", "", ""),
                new PrTrackingProps.Gitlab(globalApiBaseUrl, "token"),
                new PrTrackingProps.SlaDiscovery(Duration.ofHours(1)));
    }

    private static PrTrackingProps.Repository gitHubRepo(String name) {
        return new PrTrackingProps.Repository(
                name,
                "team",
                Provider.GITHUB,
                null,
                null,
                List.of(),
                new PrTrackingProps.Sla(null, Duration.ofHours(24), null),
                null,
                null);
    }

    private static PrTrackingProps.Repository gitLabRepo(
            String name, PrTrackingProps.@org.jspecify.annotations.Nullable Gitlab perRepo) {
        return new PrTrackingProps.Repository(
                name,
                "team",
                Provider.GITLAB,
                null,
                "team-group",
                List.of(),
                new PrTrackingProps.Sla(null, Duration.ofHours(24), null),
                perRepo,
                null);
    }
}
