package com.coreeng.supportbot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class PrTrackingConfigValidationTest {

    @Test
    void acceptsValidTokenModeWhenEnabled() {
        // when / then
        assertThatCode(() -> new PrTrackingProps(
                        true,
                        "0 0 9-18 * * 1-5",
                        "pr",
                        List.of("tag"),
                        "low",
                        List.of(validRepo()),
                        validTokenGithub()))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsDuplicateRepositoryNamesIgnoringCase() {
        // given
        PrTrackingProps.Repository repoA = validRepoWithName("my-org/onboarding-repo");
        PrTrackingProps.Repository repoB = validRepoWithName("MY-ORG/ONBOARDING-REPO");

        // when / then
        assertThatThrownBy(() -> new PrTrackingProps(
                        true,
                        "0 0 9-18 * * 1-5",
                        "pr",
                        List.of("tag"),
                        "low",
                        List.of(repoA, repoB),
                        validTokenGithub()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contains duplicates");
    }

    @Test
    void rejectsMissingTokenWhenTokenModeEnabled() {
        // given
        PrTrackingProps.GitHub noToken =
                new PrTrackingProps.GitHub(PrTrackingProps.AuthMode.TOKEN, "https://api.github.com", "", "", "", "");

        // when / then
        assertThatThrownBy(() -> new PrTrackingProps(
                        true, "0 0 9-18 * * 1-5", "pr", List.of("tag"), "low", List.of(validRepo()), noToken))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("github.token");
    }

    @Test
    void rejectsMissingGitHubAppFieldsWhenAppModeEnabled() {
        // given
        PrTrackingProps.GitHub appNoInstallation =
                new PrTrackingProps.GitHub(PrTrackingProps.AuthMode.APP, "https://api.github.com", "", "12345", "", "");

        // when / then
        assertThatThrownBy(() -> new PrTrackingProps(
                        true, "0 0 9-18 * * 1-5", "pr", List.of("tag"), "low", List.of(validRepo()), appNoInstallation))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("installation-id");
    }

    @Test
    void rejectsMissingGitHubConfigWhenEnabled() {
        // when / then
        assertThatThrownBy(() -> new PrTrackingProps(
                        true, "0 0 9-18 * * 1-5", "pr", List.of("tag"), "low", List.of(validRepo()), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pr-review-tracking.github must be configured when enabled");
    }

    @Test
    void rejectsMissingAuthModeWhenEnabled() {
        // given
        PrTrackingProps.GitHub noAuthMode =
                new PrTrackingProps.GitHub(null, "https://api.github.com", "pat-123", "", "", "");

        // when / then
        assertThatThrownBy(() -> new PrTrackingProps(
                        true, "0 0 9-18 * * 1-5", "pr", List.of("tag"), "low", List.of(validRepo()), noAuthMode))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("github.auth-mode");
    }

    @Test
    void acceptsValidAppModeWhenEnabled() {
        // given
        PrTrackingProps.GitHub appConfig = new PrTrackingProps.GitHub(
                PrTrackingProps.AuthMode.APP,
                "https://api.github.com",
                "",
                "12345",
                "67890",
                "-----BEGIN RSA PRIVATE KEY-----");

        // when / then
        assertThatCode(() -> new PrTrackingProps(
                        true, "0 0 9-18 * * 1-5", "pr", List.of("tag"), "low", List.of(validRepo()), appConfig))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsRepoNameWithExtraSlashes() {
        // given
        PrTrackingProps.Repository badName =
                new PrTrackingProps.Repository("my-org/sub/repo", "wow", Duration.ofDays(2));

        // when / then
        assertThatThrownBy(() -> new PrTrackingProps(
                        true, "0 0 9-18 * * 1-5", "pr", List.of("tag"), "low", List.of(badName), validTokenGithub()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("org/repo format");
    }

    @Test
    void rejectsZeroSlaWhenEnabled() {
        // given
        PrTrackingProps.Repository zeroSla = new PrTrackingProps.Repository("my-org/repo", "wow", Duration.ZERO);

        // when / then
        assertThatThrownBy(() -> new PrTrackingProps(
                        true, "0 0 9-18 * * 1-5", "pr", List.of("tag"), "low", List.of(zeroSla), validTokenGithub()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive duration");
    }

    @Test
    void rejectsEmptyTagsWhenEnabled() {
        // when / then
        assertThatThrownBy(() -> new PrTrackingProps(
                        true, "0 0 9-18 * * 1-5", "pr", List.of(), "low", List.of(validRepo()), validTokenGithub()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tags must not be empty");
    }

    @Test
    void rejectsBlankImpactWhenEnabled() {
        // when / then
        assertThatThrownBy(() -> new PrTrackingProps(
                        true, "0 0 9-18 * * 1-5", "pr", List.of("tag"), "", List.of(validRepo()), validTokenGithub()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("impact must not be blank");
    }

    @Test
    void rejectsEmptyRepositoryListWhenEnabled() {
        // when / then
        assertThatThrownBy(() -> new PrTrackingProps(
                        true, "0 0 9-18 * * 1-5", "pr", List.of("tag"), "low", List.of(), validTokenGithub()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be empty");
    }

    @Test
    void skipsAllValidationWhenDisabled() {
        // given
        PrTrackingProps.Repository badRepo = new PrTrackingProps.Repository("", "", Duration.ZERO);

        // when / then
        assertThatCode(() -> new PrTrackingProps(
                        false, "", null, null, null, List.of(badRepo), PrTrackingProps.GitHub.defaultTokenModeConfig()))
                .doesNotThrowAnyException();
    }

    @Test
    void normalizesRepositoryNamesToLowerCase() {
        // given
        PrTrackingProps.Repository mixedCase =
                new PrTrackingProps.Repository("My-Org/My-Repo", "wow", Duration.ofDays(2));

        // when
        PrTrackingProps props = new PrTrackingProps(
                true, "0 0 9-18 * * 1-5", "pr", List.of("tag"), "low", List.of(mixedCase), validTokenGithub());

        // then
        assertThat(props.repositories())
                .singleElement()
                .extracting(PrTrackingProps.Repository::name)
                .isEqualTo("my-org/my-repo");
    }

    private static PrTrackingProps.Repository validRepo() {
        return validRepoWithName("my-org/onboarding-repo");
    }

    private static PrTrackingProps.Repository validRepoWithName(String name) {
        return new PrTrackingProps.Repository(name, "wow", Duration.ofDays(2));
    }

    private static PrTrackingProps.GitHub validTokenGithub() {
        return new PrTrackingProps.GitHub(
                PrTrackingProps.AuthMode.TOKEN, "https://api.github.com", "pat-123", "", "", "");
    }
}
