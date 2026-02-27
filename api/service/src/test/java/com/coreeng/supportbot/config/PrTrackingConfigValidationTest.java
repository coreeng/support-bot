package com.coreeng.supportbot.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class PrTrackingConfigValidationTest {

    @Test
    void acceptsValidTokenModeWhenEnabled() {
        // when / then
        assertThatCode(() -> new PrTrackingProps(true, "0 0 9-18 * * 1-5", "pr", List.of("tag"), "low", List.of(validRepo()), validTokenGithub()))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsDuplicateRepositoryNamesIgnoringCase() {
        // given
        PrTrackingRepositoryProps repoA = validRepoWithName("my-org/onboarding-repo");
        PrTrackingRepositoryProps repoB = validRepoWithName("MY-ORG/ONBOARDING-REPO");

        // when / then
        assertThatThrownBy(() -> new PrTrackingProps(
                        true, "0 0 9-18 * * 1-5", "pr", List.of("tag"), "low", List.of(repoA, repoB), validTokenGithub()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contains duplicates");
    }

    @Test
    void rejectsMissingTokenWhenTokenModeEnabled() {
        // given
        PrTrackingGitHubProps noToken = new PrTrackingGitHubProps(
                PrTrackingAuthMode.TOKEN, "https://api.github.com", "", "", "", "");

        // when / then
        assertThatThrownBy(() -> new PrTrackingProps(true, "0 0 9-18 * * 1-5", "pr", List.of("tag"), "low", List.of(validRepo()), noToken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("github.token");
    }

    @Test
    void rejectsMissingGitHubAppFieldsWhenAppModeEnabled() {
        // given
        PrTrackingGitHubProps appNoInstallation = new PrTrackingGitHubProps(
                PrTrackingAuthMode.APP, "https://api.github.com", "", "12345", "", "");

        // when / then
        assertThatThrownBy(() -> new PrTrackingProps(true, "0 0 9-18 * * 1-5", "pr", List.of("tag"), "low", List.of(validRepo()), appNoInstallation))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("installation-id");
    }

    @Test
    void acceptsValidAppModeWhenEnabled() {
        // given
        PrTrackingGitHubProps appConfig = new PrTrackingGitHubProps(
                PrTrackingAuthMode.APP, "https://api.github.com", "", "12345", "67890", "-----BEGIN RSA PRIVATE KEY-----");

        // when / then
        assertThatCode(() -> new PrTrackingProps(true, "0 0 9-18 * * 1-5", "pr", List.of("tag"), "low", List.of(validRepo()), appConfig))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsRepoNameWithExtraSlashes() {
        // given
        PrTrackingRepositoryProps badName = new PrTrackingRepositoryProps(
                "my-org/sub/repo", "wow", Duration.ofDays(2));

        // when / then
        assertThatThrownBy(() -> new PrTrackingProps(true, "0 0 9-18 * * 1-5", "pr", List.of("tag"), "low", List.of(badName), validTokenGithub()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("org/repo format");
    }

    @Test
    void rejectsZeroSlaWhenEnabled() {
        // given
        PrTrackingRepositoryProps zeroSla = new PrTrackingRepositoryProps(
                "my-org/repo", "wow", Duration.ZERO);

        // when / then
        assertThatThrownBy(() -> new PrTrackingProps(true, "0 0 9-18 * * 1-5", "pr", List.of("tag"), "low", List.of(zeroSla), validTokenGithub()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive duration");
    }

    @Test
    void rejectsEmptyTagsWhenEnabled() {
        // when / then
        assertThatThrownBy(() -> new PrTrackingProps(true, "0 0 9-18 * * 1-5", "pr", List.of(), "low", List.of(validRepo()), validTokenGithub()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tags must not be empty");
    }

    @Test
    void rejectsBlankImpactWhenEnabled() {
        // when / then
        assertThatThrownBy(() -> new PrTrackingProps(true, "0 0 9-18 * * 1-5", "pr", List.of("tag"), "", List.of(validRepo()), validTokenGithub()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("impact must not be blank");
    }

    @Test
    void rejectsEmptyRepositoryListWhenEnabled() {
        // when / then
        assertThatThrownBy(() -> new PrTrackingProps(true, "0 0 9-18 * * 1-5", "pr", List.of("tag"), "low", List.of(), validTokenGithub()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be empty");
    }

    @Test
    void skipsAllValidationWhenDisabled() {
        // given
        PrTrackingRepositoryProps badRepo = new PrTrackingRepositoryProps("", "", Duration.ZERO);

        // when / then
        assertThatCode(() -> new PrTrackingProps(false, "", null, null, null, List.of(badRepo), PrTrackingGitHubProps.defaultTokenModeConfig()))
                .doesNotThrowAnyException();
    }

    private static PrTrackingRepositoryProps validRepo() {
        return validRepoWithName("my-org/onboarding-repo");
    }

    private static PrTrackingRepositoryProps validRepoWithName(String name) {
        return new PrTrackingRepositoryProps(name, "wow", Duration.ofDays(2));
    }

    private static PrTrackingGitHubProps validTokenGithub() {
        return new PrTrackingGitHubProps(PrTrackingAuthMode.TOKEN, "https://api.github.com", "pat-123", "", "", "");
    }
}
