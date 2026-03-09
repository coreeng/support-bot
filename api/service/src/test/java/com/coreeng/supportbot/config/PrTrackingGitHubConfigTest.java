package com.coreeng.supportbot.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coreeng.supportbot.github.GitHubClient;
import com.coreeng.supportbot.prtracking.PrTrackingGitHubConfig;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
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

    @Test
    void createsGitHubBeanInAppModeWithValidPrivateKeyPem() throws Exception {
        // given
        PrTrackingGitHubConfig config = new PrTrackingGitHubConfig();
        PrTrackingProps.GitHub appGithub = new PrTrackingProps.GitHub(
                PrTrackingProps.AuthMode.APP,
                "https://api.github.com",
                "",
                "12345",
                "67890",
                toPem(generatePrivateKey()));
        PrTrackingProps props = new PrTrackingProps(
                true,
                "0 0 9-18 * * 1-5",
                "pr",
                List.of("pr-review"),
                "low",
                List.of(new PrTrackingProps.Repository("my-org/my-repo", "wow", Duration.ofDays(2))),
                appGithub);

        // when
        GitHub gitHub = config.gitHub(props);

        // then
        assertThat(gitHub).isNotNull();
        assertThat(config.gitHubClient(gitHub)).isNotNull().isInstanceOf(GitHubClient.class);
    }

    @Test
    void createsGitHubBeanInAppModeWithBase64EncodedPrivateKeyPem() throws Exception {
        // given
        PrTrackingGitHubConfig config = new PrTrackingGitHubConfig();
        String pem = toPem(generatePrivateKey());
        String pemBase64 = Base64.getEncoder().encodeToString(pem.getBytes(UTF_8));
        PrTrackingProps.GitHub appGithub = new PrTrackingProps.GitHub(
                PrTrackingProps.AuthMode.APP, "https://api.github.com", "", "12345", "67890", pemBase64);
        PrTrackingProps props = new PrTrackingProps(
                true,
                "0 0 9-18 * * 1-5",
                "pr",
                List.of("pr-review"),
                "low",
                List.of(new PrTrackingProps.Repository("my-org/my-repo", "wow", Duration.ofDays(2))),
                appGithub);

        // when
        GitHub gitHub = config.gitHub(props);

        // then
        assertThat(gitHub).isNotNull();
        assertThat(config.gitHubClient(gitHub)).isNotNull().isInstanceOf(GitHubClient.class);
    }

    @Test
    void failsFastWhenAppModePrivateKeyIsNeitherPemNorBase64Pem() {
        // given
        PrTrackingGitHubConfig config = new PrTrackingGitHubConfig();
        PrTrackingProps.GitHub appGithub = new PrTrackingProps.GitHub(
                PrTrackingProps.AuthMode.APP,
                "https://api.github.com",
                "",
                "12345",
                "67890",
                "not-a-pem-and-not-base64");
        PrTrackingProps props = new PrTrackingProps(
                true,
                "0 0 9-18 * * 1-5",
                "pr",
                List.of("pr-review"),
                "low",
                List.of(new PrTrackingProps.Repository("my-org/my-repo", "wow", Duration.ofDays(2))),
                appGithub);

        // when / then
        assertThatThrownBy(() -> config.gitHub(props))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unrecognised PEM object");
    }

    private static PrivateKey generatePrivateKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair().getPrivate();
    }

    private static String toPem(PrivateKey privateKey) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes(UTF_8)).encodeToString(privateKey.getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----";
    }

    @Configuration
    @EnableConfigurationProperties(PrTrackingProps.class)
    static class TestConfig {}
}
