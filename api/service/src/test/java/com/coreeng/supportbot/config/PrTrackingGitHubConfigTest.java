package com.coreeng.supportbot.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.github.GitHubAppInstallationTokenClient;
import com.coreeng.supportbot.github.GitHubAuthTokenProvider;
import com.coreeng.supportbot.github.GitHubClient;
import com.coreeng.supportbot.github.GitHubInstallationToken;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class PrTrackingGitHubConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(WithAppTokenClient.class, PrTrackingGitHubConfig.class);

    @Test
    void doesNotCreateGitHubBeansWhenFeatureDisabled() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(GitHubAuthTokenProvider.class);
            assertThat(context).doesNotHaveBean(GitHubClient.class);
        });
    }

    @Test
    void createsGitHubBeansWhenFeatureEnabledInTokenMode() {
        contextRunner
                .withPropertyValues(
                        "pr-review-tracking.enabled=true",
                        "pr-review-tracking.repositories[0].name=my-org/my-repo",
                        "pr-review-tracking.repositories[0].owning-team=wow",
                        "pr-review-tracking.repositories[0].sla=PT48H",
                        "pr-review-tracking.github.api-base-url=https://api.github.com",
                        "pr-review-tracking.github.auth-mode=token",
                        "pr-review-tracking.github.token=test-token")
                .run(context -> {
                    assertThat(context).hasSingleBean(GitHubAuthTokenProvider.class);
                    assertThat(context).hasSingleBean(GitHubClient.class);
                });
    }

    @Test
    void createsGitHubBeansWhenFeatureEnabledInAppMode() {
        contextRunner
                .withPropertyValues(
                        "pr-review-tracking.enabled=true",
                        "pr-review-tracking.repositories[0].name=my-org/my-repo",
                        "pr-review-tracking.repositories[0].owning-team=wow",
                        "pr-review-tracking.repositories[0].sla=PT48H",
                        "pr-review-tracking.github.api-base-url=https://api.github.com",
                        "pr-review-tracking.github.auth-mode=app",
                        "pr-review-tracking.github.app-id=app-id",
                        "pr-review-tracking.github.installation-id=inst-id",
                        "pr-review-tracking.github.private-key-pem=private-key")
                .run(context -> {
                    assertThat(context).hasSingleBean(GitHubAuthTokenProvider.class);
                    assertThat(context).hasSingleBean(GitHubClient.class);
                });
    }

    @Test
    void failsWhenAppModeEnabledWithoutInstallationTokenClientBean() {
        new ApplicationContextRunner()
                .withUserConfiguration(WithoutAppTokenClient.class, PrTrackingGitHubConfig.class)
                .withPropertyValues(
                        "pr-review-tracking.enabled=true",
                        "pr-review-tracking.repositories[0].name=my-org/my-repo",
                        "pr-review-tracking.repositories[0].owning-team=wow",
                        "pr-review-tracking.repositories[0].sla=PT48H",
                        "pr-review-tracking.github.api-base-url=https://api.github.com",
                        "pr-review-tracking.github.auth-mode=app",
                        "pr-review-tracking.github.app-id=app-id",
                        "pr-review-tracking.github.installation-id=inst-id",
                        "pr-review-tracking.github.private-key-pem=private-key")
                .run(context -> {
                    assertThat(context.getStartupFailure()).hasMessageContaining("GitHubAppInstallationTokenClient");
                });
    }

    @Configuration
    @EnableConfigurationProperties(PrTrackingProps.class)
    static class WithAppTokenClient {
        @Bean
        GitHubAppInstallationTokenClient gitHubAppInstallationTokenClient() {
            return (apiBaseUrl, appId, installationId, privateKeyPem) ->
                    new GitHubInstallationToken("app-token", Instant.parse("2030-01-01T00:00:00Z"));
        }
    }

    @Configuration
    @EnableConfigurationProperties(PrTrackingProps.class)
    static class WithoutAppTokenClient {}
}
