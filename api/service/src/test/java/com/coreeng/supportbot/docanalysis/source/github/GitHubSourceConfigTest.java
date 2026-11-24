package com.coreeng.supportbot.docanalysis.source.github;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.config.DocumentationAnalysisProps;
import com.coreeng.supportbot.config.PrTrackingProps;
import com.coreeng.supportbot.github.GitHubClient;
import com.coreeng.supportbot.prtracking.PrTrackingGitHubConfig;
import com.coreeng.supportbot.prtracking.source.PrSourceClient;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kohsuke.github.GitHub;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class GitHubSourceConfigTest {

    @TempDir
    Path copyLocation;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class, PrTrackingGitHubConfig.class, GitHubSourceConfig.class);

    @Test
    void startsWithPrTrackingOffAndTheGitHubSourceOn() {
        contextRunner
                .withPropertyValues(enabled())
                .withPropertyValues("pr-review-tracking.github.token=test-token")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(GitHub.class);
                    assertThat(context).hasSingleBean(GitHubClient.class);
                    assertThat(context).hasSingleBean(DocumentationCopy.class);
                    assertThat(context).doesNotHaveBean(PrSourceClient.class);
                });
    }

    @Test
    void doesNotStartWithTheGitHubSourceOnAndNoGitHubCredentials() {
        contextRunner.withPropertyValues(enabled()).run(context -> assertThat(context)
                .getFailure()
                .hasStackTraceContaining("pr-review-tracking.github.token must not be blank"));
    }

    @Test
    void doesNotStartWithTheGitHubSourceOnAndAnIncompleteGitHubApp() {
        contextRunner
                .withPropertyValues(enabled())
                .withPropertyValues("pr-review-tracking.github.auth-mode=app", "pr-review-tracking.github.app-id=1")
                .run(context -> assertThat(context)
                        .getFailure()
                        .hasStackTraceContaining("pr-review-tracking.github.installation-id must not be blank"));
    }

    @Test
    void checksTheCredentialsWithoutAnLlmProviderButKeepsNoCopy() {
        contextRunner
                .withPropertyValues(enabled())
                .withPropertyValues("llm.provider=none")
                .run(context -> assertThat(context)
                        .getFailure()
                        .hasStackTraceContaining("pr-review-tracking.github.token must not be blank"));
        contextRunner
                .withPropertyValues(enabled())
                .withPropertyValues("llm.provider=none", "pr-review-tracking.github.token=test-token")
                .run(context -> assertThat(context).doesNotHaveBean(DocumentationCopy.class));
    }

    @Test
    void createsNothingWhileBothFeaturesAreOff() {
        contextRunner.withPropertyValues("llm.provider=vertex").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(GitHub.class);
            assertThat(context).doesNotHaveBean(GitHubClient.class);
            assertThat(context).doesNotHaveBean(DocumentationCopy.class);
        });
    }

    private String[] enabled() {
        return new String[] {
            "llm.provider=vertex",
            "documentation-analysis.enabled=true",
            "documentation-analysis.source.github.repository=example-org/docs",
            "documentation-analysis.source.github.copy-location=" + copyLocation,
            "pr-review-tracking.github.api-base-url=https://api.github.com",
            "pr-review-tracking.github.auth-mode=token"
        };
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({PrTrackingProps.class, DocumentationAnalysisProps.class})
    static class TestConfig {

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }
}
