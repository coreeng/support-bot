package com.coreeng.supportbot.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.github.GitHubClient;
import com.coreeng.supportbot.prtracking.PrTrackingGitHubConfig;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GitHub;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class PrTrackingGitHubConfigTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class, PrTrackingGitHubConfig.class);

    @Test
    void doesNotCreateGitHubBeansWhenFeatureDisabled() {
        // when / then
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(GitHub.class);
            assertThat(context).doesNotHaveBean(GitHubClient.class);
        });
    }

    @Test
    void createsGitHubBeansWhenFeatureEnabledInTokenMode() {
        // given
        var runner = contextRunner.withPropertyValues(
                "pr-review-tracking.enabled=true",
                "pr-review-tracking.tags[0]=pr-review",
                "pr-review-tracking.impact=low",
                "pr-review-tracking.repositories[0].name=my-org/my-repo",
                "pr-review-tracking.repositories[0].owning-team=wow",
                "pr-review-tracking.repositories[0].sla=PT48H",
                "pr-review-tracking.github.api-base-url=https://api.github.com",
                "pr-review-tracking.github.auth-mode=token",
                "pr-review-tracking.github.token=test-token");

        // when / then
        runner.run(context -> {
            assertThat(context).hasSingleBean(GitHub.class);
            assertThat(context).hasSingleBean(GitHubClient.class);
        });
    }

    @Configuration
    @EnableConfigurationProperties(PrTrackingProps.class)
    static class TestConfig {}
}
