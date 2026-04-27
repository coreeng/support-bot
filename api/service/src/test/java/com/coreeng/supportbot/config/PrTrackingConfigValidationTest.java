package com.coreeng.supportbot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class PrTrackingConfigValidationTest {

    private static final String DEFAULT_DURATION_UNIT = "days";
    private static final PrTrackingProps.SlaDiscovery DEFAULT_SLA_DISCOVERY =
            new PrTrackingProps.SlaDiscovery(Duration.ofHours(24));

    @Test
    void acceptsValidTokenModeWhenEnabled() {
        // when / then
        assertThatCode(() -> new PrTrackingProps(
                        true,
                        "0 0 9-18 * * 1-5",
                        "pr",
                        List.of("tag"),
                        "low",
                        DEFAULT_DURATION_UNIT,
                        List.of(validRepo()),
                        validTokenGithub(),
                        DEFAULT_SLA_DISCOVERY))
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
                        DEFAULT_DURATION_UNIT,
                        List.of(repoA, repoB),
                        validTokenGithub(),
                        DEFAULT_SLA_DISCOVERY))
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
                        true,
                        "0 0 9-18 * * 1-5",
                        "pr",
                        List.of("tag"),
                        "low",
                        DEFAULT_DURATION_UNIT,
                        List.of(validRepo()),
                        noToken,
                        DEFAULT_SLA_DISCOVERY))
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
                        true,
                        "0 0 9-18 * * 1-5",
                        "pr",
                        List.of("tag"),
                        "low",
                        DEFAULT_DURATION_UNIT,
                        List.of(validRepo()),
                        appNoInstallation,
                        DEFAULT_SLA_DISCOVERY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("installation-id");
    }

    @Test
    void rejectsMissingGitHubConfigWhenEnabled() {
        // when / then
        assertThatThrownBy(() -> new PrTrackingProps(
                        true,
                        "0 0 9-18 * * 1-5",
                        "pr",
                        List.of("tag"),
                        "low",
                        DEFAULT_DURATION_UNIT,
                        List.of(validRepo()),
                        null,
                        DEFAULT_SLA_DISCOVERY))
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
                        true,
                        "0 0 9-18 * * 1-5",
                        "pr",
                        List.of("tag"),
                        "low",
                        DEFAULT_DURATION_UNIT,
                        List.of(validRepo()),
                        noAuthMode,
                        DEFAULT_SLA_DISCOVERY))
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
                        true,
                        "0 0 9-18 * * 1-5",
                        "pr",
                        List.of("tag"),
                        "low",
                        DEFAULT_DURATION_UNIT,
                        List.of(validRepo()),
                        appConfig,
                        DEFAULT_SLA_DISCOVERY))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsRepoNameWithExtraSlashes() {
        // given
        PrTrackingProps.Repository badName =
                new PrTrackingProps.Repository("my-org/sub/repo", "wow", null, List.of(), sla(Duration.ofDays(2)));

        // when / then
        assertThatThrownBy(() -> new PrTrackingProps(
                        true,
                        "0 0 9-18 * * 1-5",
                        "pr",
                        List.of("tag"),
                        "low",
                        DEFAULT_DURATION_UNIT,
                        List.of(badName),
                        validTokenGithub(),
                        DEFAULT_SLA_DISCOVERY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("org/repo format");
    }

    @Test
    void rejectsZeroSlaWhenEnabled() {
        // given
        PrTrackingProps.Repository zeroSla =
                new PrTrackingProps.Repository("my-org/repo", "wow", null, List.of(), sla(Duration.ZERO));

        // when / then
        assertThatThrownBy(() -> new PrTrackingProps(
                        true,
                        "0 0 9-18 * * 1-5",
                        "pr",
                        List.of("tag"),
                        "low",
                        DEFAULT_DURATION_UNIT,
                        List.of(zeroSla),
                        validTokenGithub(),
                        DEFAULT_SLA_DISCOVERY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sla.default must be a positive duration");
    }

    @Test
    void rejectsNegativeSlaWhenEnabled() {
        // given
        PrTrackingProps.Repository negativeSla =
                new PrTrackingProps.Repository("my-org/repo", "wow", null, List.of(), sla(Duration.ofHours(-1)));

        // when / then
        assertThatThrownBy(() -> new PrTrackingProps(
                        true,
                        "0 0 9-18 * * 1-5",
                        "pr",
                        List.of("tag"),
                        "low",
                        DEFAULT_DURATION_UNIT,
                        List.of(negativeSla),
                        validTokenGithub(),
                        DEFAULT_SLA_DISCOVERY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sla.default must be a positive duration");
    }

    @Test
    void rejectsNullDefaultSlaWhenEnabled() {
        // given
        PrTrackingProps.Sla slaWithNullDefault = new PrTrackingProps.Sla(null, null, null, null);
        PrTrackingProps.Repository repo =
                new PrTrackingProps.Repository("my-org/repo", "wow", null, List.of(), slaWithNullDefault);

        // when / then
        assertThatThrownBy(() -> new PrTrackingProps(
                        true,
                        "0 0 9-18 * * 1-5",
                        "pr",
                        List.of("tag"),
                        "low",
                        DEFAULT_DURATION_UNIT,
                        List.of(repo),
                        validTokenGithub(),
                        DEFAULT_SLA_DISCOVERY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sla.default must be set when sla.file is not configured");
    }

    @Test
    void rejectsZeroOverrideSlaWhenEnabled() {
        // given
        PrTrackingProps.Sla slaWithBadOverride = new PrTrackingProps.Sla(
                null, Duration.ofDays(2), List.of(new PrTrackingProps.SlaOverride("infra/**", Duration.ZERO)), null);
        PrTrackingProps.Repository repo =
                new PrTrackingProps.Repository("my-org/repo", "wow", null, List.of(), slaWithBadOverride);

        // when / then
        assertThatThrownBy(() -> new PrTrackingProps(
                        true,
                        "0 0 9-18 * * 1-5",
                        "pr",
                        List.of("tag"),
                        "low",
                        DEFAULT_DURATION_UNIT,
                        List.of(repo),
                        validTokenGithub(),
                        DEFAULT_SLA_DISCOVERY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overrides[].sla must be a positive duration");
    }

    @Test
    void rejectsBlankOverridePathWhenEnabled() {
        // given
        PrTrackingProps.Sla slaWithBlankPath = new PrTrackingProps.Sla(
                null, Duration.ofDays(2), List.of(new PrTrackingProps.SlaOverride("  ", Duration.ofDays(7))), null);
        PrTrackingProps.Repository repo =
                new PrTrackingProps.Repository("my-org/repo", "wow", null, List.of(), slaWithBlankPath);

        // when / then
        assertThatThrownBy(() -> new PrTrackingProps(
                        true,
                        "0 0 9-18 * * 1-5",
                        "pr",
                        List.of("tag"),
                        "low",
                        DEFAULT_DURATION_UNIT,
                        List.of(repo),
                        validTokenGithub(),
                        DEFAULT_SLA_DISCOVERY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overrides[].path must not be blank");
    }

    @Test
    void acceptsValidSlaWithFileAndOverrides() {
        // given
        PrTrackingProps.Sla fullSla = new PrTrackingProps.Sla(
                ".pr-sla.yaml",
                Duration.ofDays(2),
                List.of(new PrTrackingProps.SlaOverride("infra/**", Duration.ofDays(7))),
                null);
        PrTrackingProps.Repository repo =
                new PrTrackingProps.Repository("my-org/repo", "wow", null, List.of(), fullSla);

        // when / then
        assertThatCode(() -> new PrTrackingProps(
                        true,
                        "0 0 9-18 * * 1-5",
                        "pr",
                        List.of("tag"),
                        "low",
                        DEFAULT_DURATION_UNIT,
                        List.of(repo),
                        validTokenGithub(),
                        DEFAULT_SLA_DISCOVERY))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsRepoWithEscalationMessage() {
        // given
        PrTrackingProps.Sla slaWithEscalationMessage =
                new PrTrackingProps.Sla(null, Duration.ofDays(2), null, "Contact #pr-reviews to chase review.");
        PrTrackingProps.Repository repo =
                new PrTrackingProps.Repository("my-org/repo", "wow", null, List.of(), slaWithEscalationMessage);

        // when / then
        assertThatCode(() -> new PrTrackingProps(
                        true,
                        "0 0 9-18 * * 1-5",
                        "pr",
                        List.of("tag"),
                        "low",
                        DEFAULT_DURATION_UNIT,
                        List.of(repo),
                        validTokenGithub(),
                        DEFAULT_SLA_DISCOVERY))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsBlankEscalationMessage() {
        // given
        PrTrackingProps.Sla slaWithBlankMessage = new PrTrackingProps.Sla(null, Duration.ofDays(2), null, "   ");
        PrTrackingProps.Repository repo =
                new PrTrackingProps.Repository("my-org/repo", "wow", null, List.of(), slaWithBlankMessage);

        // when / then
        assertThatThrownBy(() -> new PrTrackingProps(
                        true,
                        "0 0 9-18 * * 1-5",
                        "pr",
                        List.of("tag"),
                        "low",
                        DEFAULT_DURATION_UNIT,
                        List.of(repo),
                        validTokenGithub(),
                        DEFAULT_SLA_DISCOVERY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escalation-message must not be blank");
    }

    @Test
    void acceptsFileOnlyRepoWithNoDefaultSla() {
        // given (file set, no default, no overrides)
        PrTrackingProps.Sla fileOnlySla = new PrTrackingProps.Sla(".pr-sla.yaml", null, null, null);
        PrTrackingProps.Repository repo =
                new PrTrackingProps.Repository("my-org/repo", "wow", null, List.of(), fileOnlySla);

        // when / then
        assertThatCode(() -> new PrTrackingProps(
                        true,
                        "0 0 9-18 * * 1-5",
                        "pr",
                        List.of("tag"),
                        "low",
                        DEFAULT_DURATION_UNIT,
                        List.of(repo),
                        validTokenGithub(),
                        DEFAULT_SLA_DISCOVERY))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsEmptyTagsWhenEnabled() {
        // when / then
        assertThatThrownBy(() -> new PrTrackingProps(
                        true,
                        "0 0 9-18 * * 1-5",
                        "pr",
                        List.of(),
                        "low",
                        DEFAULT_DURATION_UNIT,
                        List.of(validRepo()),
                        validTokenGithub(),
                        DEFAULT_SLA_DISCOVERY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tags must not be empty");
    }

    @Test
    void rejectsBlankImpactWhenEnabled() {
        // when / then
        assertThatThrownBy(() -> new PrTrackingProps(
                        true,
                        "0 0 9-18 * * 1-5",
                        "pr",
                        List.of("tag"),
                        "",
                        DEFAULT_DURATION_UNIT,
                        List.of(validRepo()),
                        validTokenGithub(),
                        DEFAULT_SLA_DISCOVERY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("impact must not be blank");
    }

    @Test
    void rejectsEmptyRepositoryListWhenEnabled() {
        // when / then
        assertThatThrownBy(() -> new PrTrackingProps(
                        true,
                        "0 0 9-18 * * 1-5",
                        "pr",
                        List.of("tag"),
                        "low",
                        DEFAULT_DURATION_UNIT,
                        List.of(),
                        validTokenGithub(),
                        DEFAULT_SLA_DISCOVERY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be empty");
    }

    @Test
    void skipsAllValidationWhenDisabled() {
        // given
        PrTrackingProps.Repository badRepo =
                new PrTrackingProps.Repository("", "", null, List.of(), sla(Duration.ZERO));

        // when / then
        assertThatCode(() -> new PrTrackingProps(
                        false,
                        "",
                        null,
                        null,
                        null,
                        null,
                        List.of(badRepo),
                        PrTrackingProps.GitHub.defaultTokenModeConfig(),
                        DEFAULT_SLA_DISCOVERY))
                .doesNotThrowAnyException();
    }

    @Test
    void normalizesRepositoryNamesToLowerCase() {
        // given
        PrTrackingProps.Repository mixedCase =
                new PrTrackingProps.Repository("My-Org/My-Repo", "wow", null, List.of(), sla(Duration.ofDays(2)));

        // when
        PrTrackingProps props = new PrTrackingProps(
                true,
                "0 0 9-18 * * 1-5",
                "pr",
                List.of("tag"),
                "low",
                DEFAULT_DURATION_UNIT,
                List.of(mixedCase),
                validTokenGithub(),
                DEFAULT_SLA_DISCOVERY);

        // then
        assertThat(props.repositories())
                .singleElement()
                .extracting(PrTrackingProps.Repository::name)
                .isEqualTo("my-org/my-repo");
    }

    @Test
    void rejectsInvalidDurationUnitWhenEnabled() {
        // given an unsupported duration unit e.g. "minutes"
        String invalidUnit = "minutes";

        // when config is created with an invalid duration unit
        // then it throws
        assertThatThrownBy(() -> new PrTrackingProps(
                        true,
                        "0 0 9-18 * * 1-5",
                        "pr",
                        List.of("tag"),
                        "low",
                        invalidUnit,
                        List.of(validRepo()),
                        validTokenGithub(),
                        DEFAULT_SLA_DISCOVERY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duration-unit must be one of");
    }

    @Test
    void defaultsDurationUnitToDaysWhenNull() {
        // given no duration-unit configured
        String noDurationUnit = null;

        // when config is created without a duration unit
        PrTrackingProps props = new PrTrackingProps(
                true,
                "0 0 9-18 * * 1-5",
                "pr",
                List.of("tag"),
                "low",
                noDurationUnit,
                List.of(validRepo()),
                validTokenGithub(),
                DEFAULT_SLA_DISCOVERY);

        // then it defaults to "days"
        assertThat(props.durationUnit()).isEqualTo("days");
    }

    @Test
    void normalizesDurationUnitToLowerCase() {
        // given a duration unit with mixed case e.g. "Hours"
        String mixedCaseUnit = "Hours";

        // when config is created with a mixed case duration unit
        PrTrackingProps props = new PrTrackingProps(
                true,
                "0 0 9-18 * * 1-5",
                "pr",
                List.of("tag"),
                "low",
                mixedCaseUnit,
                List.of(validRepo()),
                validTokenGithub(),
                DEFAULT_SLA_DISCOVERY);

        // then it is normalised to lowercase
        assertThat(props.durationUnit()).isEqualTo("hours");
    }

    @Test
    void rejectsBlankGithubTeamSlug() {
        assertThatThrownBy(() ->
                        new PrTrackingProps.Repository("my-org/repo", "wow", "", List.of(), sla(Duration.ofDays(2))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("githubTeamSlug must not be blank");
    }

    @Test
    void acceptsNullGithubTeamSlug() {
        assertThatCode(() ->
                        new PrTrackingProps.Repository("my-org/repo", "wow", null, List.of(), sla(Duration.ofDays(2))))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsValidGithubTeamSlug() {
        assertThatCode(() -> new PrTrackingProps.Repository(
                        "my-org/repo", "wow", "platform-team", List.of(), sla(Duration.ofDays(2))))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsNoSlaRepoWithPaths() {
        // given
        PrTrackingProps.Repository noSlaRepo =
                new PrTrackingProps.Repository("my-org/repo", "wow", null, List.of("infra/**"), null);

        // when / then
        assertThatCode(() -> new PrTrackingProps(
                        true,
                        "0 0 9-18 * * 1-5",
                        "pr",
                        List.of("tag"),
                        "low",
                        DEFAULT_DURATION_UNIT,
                        List.of(noSlaRepo),
                        validTokenGithub(),
                        DEFAULT_SLA_DISCOVERY))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsNoSlaRepoWithoutPaths() {
        // given
        PrTrackingProps.Repository noSlaNoPathsRepo =
                new PrTrackingProps.Repository("my-org/repo", "wow", null, List.of(), null);

        // when / then
        assertThatThrownBy(() -> new PrTrackingProps(
                        true,
                        "0 0 9-18 * * 1-5",
                        "pr",
                        List.of("tag"),
                        "low",
                        DEFAULT_DURATION_UNIT,
                        List.of(noSlaNoPathsRepo),
                        validTokenGithub(),
                        DEFAULT_SLA_DISCOVERY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("paths must not be empty when sla is not configured");
    }

    @Test
    void rejectsNoSlaRepoWithBlankPath() {
        // given
        PrTrackingProps.Repository blankPathRepo =
                new PrTrackingProps.Repository("my-org/repo", "wow", null, List.of("infra/**", "  "), null);

        // when / then
        assertThatThrownBy(() -> new PrTrackingProps(
                        true,
                        "0 0 9-18 * * 1-5",
                        "pr",
                        List.of("tag"),
                        "low",
                        DEFAULT_DURATION_UNIT,
                        List.of(blankPathRepo),
                        validTokenGithub(),
                        DEFAULT_SLA_DISCOVERY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("paths[] must not be blank");
    }

    @Test
    void acceptsNoSlaRepoWithNoSlaMessage() {
        // given
        PrTrackingProps.Repository noSlaRepo = new PrTrackingProps.Repository(
                "my-org/repo",
                "wow",
                null,
                List.of("docs/**"),
                null,
                "Docs PRs have no automated SLA. Tag #docs-team if urgent.");

        // when / then
        assertThatCode(() -> new PrTrackingProps(
                        true,
                        "0 0 9-18 * * 1-5",
                        "pr",
                        List.of("tag"),
                        "low",
                        DEFAULT_DURATION_UNIT,
                        List.of(noSlaRepo),
                        validTokenGithub(),
                        DEFAULT_SLA_DISCOVERY))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsBlankNoSlaMessage() {
        // given
        PrTrackingProps.Repository repoWithBlankMessage =
                new PrTrackingProps.Repository("my-org/repo", "wow", null, List.of("docs/**"), null, "   ");

        // when / then
        assertThatThrownBy(() -> new PrTrackingProps(
                        true,
                        "0 0 9-18 * * 1-5",
                        "pr",
                        List.of("tag"),
                        "low",
                        DEFAULT_DURATION_UNIT,
                        List.of(repoWithBlankMessage),
                        validTokenGithub(),
                        DEFAULT_SLA_DISCOVERY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no-sla-message must not be blank");
    }

    private static PrTrackingProps.Repository validRepo() {
        return validRepoWithName("my-org/onboarding-repo");
    }

    private static PrTrackingProps.Repository validRepoWithName(String name) {
        return new PrTrackingProps.Repository(name, "wow", null, List.of(), sla(Duration.ofDays(2)));
    }

    private static PrTrackingProps.Sla sla(Duration defaultSla) {
        return new PrTrackingProps.Sla(null, defaultSla, null, null);
    }

    private static PrTrackingProps.GitHub validTokenGithub() {
        return new PrTrackingProps.GitHub(
                PrTrackingProps.AuthMode.TOKEN, "https://api.github.com", "pat-123", "", "", "");
    }
}
