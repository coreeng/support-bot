package com.coreeng.supportbot.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class PrTrackingConfigValidationTest {

    @Test
    void acceptsValidTokenModeWhenEnabled() {
        PrTrackingRepositoryProps repository =
                new PrTrackingRepositoryProps("my-org/onboarding-repo", "wow", Duration.ofDays(2));
        PrTrackingGitHubProps githubConfig = new PrTrackingGitHubProps(
                PrTrackingAuthMode.TOKEN,
                "https://api.github.com",
                "pat-123",
                "",
                "",
                "");

        assertThatCode(() -> new PrTrackingProps(true, "0 0 9-18 * * 1-5", List.of(repository), githubConfig))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsDuplicateRepositoryNamesIgnoringCase() {
        PrTrackingRepositoryProps repoA =
                new PrTrackingRepositoryProps("my-org/onboarding-repo", "wow", Duration.ofDays(2));
        PrTrackingRepositoryProps repoB =
                new PrTrackingRepositoryProps("MY-ORG/ONBOARDING-REPO", "infra", Duration.ofDays(1));

        assertThatThrownBy(() -> new PrTrackingProps(
                        true,
                        "0 0 9-18 * * 1-5",
                        List.of(repoA, repoB),
                        new PrTrackingGitHubProps(
                                PrTrackingAuthMode.TOKEN,
                                "https://api.github.com",
                                "pat-123",
                                "",
                                "",
                                "")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contains duplicates");
    }

    @Test
    void rejectsMissingTokenWhenTokenModeEnabled() {
        PrTrackingRepositoryProps repository =
                new PrTrackingRepositoryProps("my-org/onboarding-repo", "wow", Duration.ofDays(2));
        PrTrackingGitHubProps githubConfig = new PrTrackingGitHubProps(
                PrTrackingAuthMode.TOKEN,
                "https://api.github.com",
                "",
                "",
                "",
                "");

        assertThatThrownBy(
                        () -> new PrTrackingProps(true, "0 0 9-18 * * 1-5", List.of(repository), githubConfig))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("github.token");
    }

    @Test
    void rejectsMissingGitHubAppFieldsWhenAppModeEnabled() {
        PrTrackingRepositoryProps repository =
                new PrTrackingRepositoryProps("my-org/onboarding-repo", "wow", Duration.ofDays(2));
        PrTrackingGitHubProps githubConfig = new PrTrackingGitHubProps(
                PrTrackingAuthMode.APP,
                "https://api.github.com",
                "",
                "12345",
                "",
                "");

        assertThatThrownBy(
                        () -> new PrTrackingProps(true, "0 0 9-18 * * 1-5", List.of(repository), githubConfig))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("installation-id");
    }

    @Test
    void acceptsValidAppModeWhenEnabled() {
        PrTrackingRepositoryProps repository =
                new PrTrackingRepositoryProps("my-org/onboarding-repo", "wow", Duration.ofDays(2));
        PrTrackingGitHubProps githubConfig = new PrTrackingGitHubProps(
                PrTrackingAuthMode.APP,
                "https://api.github.com",
                "",
                "12345",
                "67890",
                "-----BEGIN RSA PRIVATE KEY-----");

        assertThatCode(() -> new PrTrackingProps(true, "0 0 9-18 * * 1-5", List.of(repository), githubConfig))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsRepoNameWithExtraSlashes() {
        PrTrackingRepositoryProps repository =
                new PrTrackingRepositoryProps("my-org/sub/repo", "wow", Duration.ofDays(2));

        assertThatThrownBy(() -> new PrTrackingProps(true, "0 0 9-18 * * 1-5", List.of(repository), validTokenGithub()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("org/repo format");
    }

    @Test
    void rejectsZeroSlaWhenEnabled() {
        PrTrackingRepositoryProps repository = new PrTrackingRepositoryProps("my-org/repo", "wow", Duration.ZERO);

        assertThatThrownBy(() -> new PrTrackingProps(true, "0 0 9-18 * * 1-5", List.of(repository), validTokenGithub()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive duration");
    }

    @Test
    void rejectsEmptyRepositoryListWhenEnabled() {
        assertThatThrownBy(() -> new PrTrackingProps(true, "0 0 9-18 * * 1-5", List.of(), validTokenGithub()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be empty");
    }

    @Test
    void skipsAllValidationWhenDisabled() {
        PrTrackingRepositoryProps badRepo = new PrTrackingRepositoryProps("", "", Duration.ZERO);

        assertThatCode(() ->
                        new PrTrackingProps(false, "", List.of(badRepo), PrTrackingGitHubProps.defaultTokenModeConfig()))
                .doesNotThrowAnyException();
    }

    private static PrTrackingGitHubProps validTokenGithub() {
        return new PrTrackingGitHubProps(PrTrackingAuthMode.TOKEN, "https://api.github.com", "pat-123", "", "", "");
    }
}
