package com.coreeng.supportbot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coreeng.supportbot.prtracking.source.Provider;
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
                        null,
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
                        null,
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
                        null,
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
                        null,
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
                        null,
                        DEFAULT_SLA_DISCOVERY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "pr-review-tracking.github must be configured when any repo uses provider=github");
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
                        null,
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
                        null,
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
                        null,
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
                        null,
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
                        null,
                        DEFAULT_SLA_DISCOVERY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sla.default must be a positive duration");
    }

    @Test
    void rejectsNullDefaultSlaWhenEnabled() {
        // given
        PrTrackingProps.Sla slaWithNullDefault = new PrTrackingProps.Sla(null, null, null);
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
                        null,
                        DEFAULT_SLA_DISCOVERY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sla.default must be set when sla.file is not configured");
    }

    @Test
    void rejectsZeroOverrideSlaWhenEnabled() {
        // given
        PrTrackingProps.Sla slaWithBadOverride = new PrTrackingProps.Sla(
                null, Duration.ofDays(2), List.of(new PrTrackingProps.SlaOverride("infra/**", Duration.ZERO)));
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
                        null,
                        DEFAULT_SLA_DISCOVERY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overrides[].sla must be a positive duration");
    }

    @Test
    void rejectsBlankOverridePathWhenEnabled() {
        // given
        PrTrackingProps.Sla slaWithBlankPath = new PrTrackingProps.Sla(
                null, Duration.ofDays(2), List.of(new PrTrackingProps.SlaOverride("  ", Duration.ofDays(7))));
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
                        null,
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
                List.of(new PrTrackingProps.SlaOverride("infra/**", Duration.ofDays(7))));
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
                        null,
                        DEFAULT_SLA_DISCOVERY))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsRepoWithMessageOverrides() {
        // given
        PrTrackingProps.Messages messages = new PrTrackingProps.Messages(
                "\"PR detected.\"", "\"Contact #pr-reviews to chase this review.\"", null, null, null, null);
        PrTrackingProps.Repository repo = new PrTrackingProps.Repository(
                "my-org/repo", "wow", null, List.of(), sla(Duration.ofDays(2)), messages);

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
                        null,
                        DEFAULT_SLA_DISCOVERY))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsBlankMessageField() {
        // Blank check lives in the Messages compact constructor, so it fires at construction time
        // regardless of whether the feature is enabled.
        assertThatThrownBy(() -> new PrTrackingProps.Messages("   ", null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("messages.detected must not be blank");
    }

    @Test
    void rejectsEscalatedMessageOnNoSlaRepo() {
        // given — escalated is meaningless on a no-SLA repo (nothing to escalate)
        PrTrackingProps.Messages messages =
                new PrTrackingProps.Messages(null, "\"Will escalate.\"", null, null, null, null);
        PrTrackingProps.Repository noSlaRepo =
                new PrTrackingProps.Repository("my-org/repo", "wow", null, List.of("docs/**"), null, messages);

        // when / then
        assertThatThrownBy(() -> new PrTrackingProps(
                        true,
                        "0 0 9-18 * * 1-5",
                        "pr",
                        List.of("tag"),
                        "low",
                        DEFAULT_DURATION_UNIT,
                        List.of(noSlaRepo),
                        validTokenGithub(),
                        null,
                        DEFAULT_SLA_DISCOVERY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("messages.escalated must not be set for no-SLA repositories");
    }

    @Test
    void acceptsFileOnlyRepoWithNoDefaultSla() {
        // given (file set, no default, no overrides)
        PrTrackingProps.Sla fileOnlySla = new PrTrackingProps.Sla(".pr-sla.yaml", null, null);
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
                        null,
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
                        null,
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
                        null,
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
                        null,
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
                        null,
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
                null,
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
                        null,
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
                null,
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
                null,
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
                        null,
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
                        null,
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
                        null,
                        DEFAULT_SLA_DISCOVERY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("paths[] must not be blank");
    }

    @Test
    void acceptsNoSlaRepoWithDetectedMessage() {
        // given — no-SLA repos can configure any message except escalated
        PrTrackingProps.Messages messages = new PrTrackingProps.Messages(
                "\"Docs PRs have no automated SLA. Tag #docs-team if urgent.\"", null, null, null, null, null);
        PrTrackingProps.Repository noSlaRepo =
                new PrTrackingProps.Repository("my-org/repo", "wow", null, List.of("docs/**"), null, messages);

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
                        null,
                        DEFAULT_SLA_DISCOVERY))
                .doesNotThrowAnyException();
    }

    // ---- GitLab provider validation -------------------------------------------------------------

    @Test
    void defaultsProviderToGithubWhenNotSpecified() {
        // given — Repository constructed without an explicit provider
        PrTrackingProps.Repository repo = validRepo();

        // then — provider defaults to GITHUB
        assertThat(repo.provider()).isEqualTo(Provider.GITHUB);
    }

    @Test
    void acceptsGitLabRepoWithNestedGroupName() {
        // given — GitLab supports group/subgroup/project names
        PrTrackingProps.Repository repo = gitlabRepo("platform/infra/cluster-config");

        // when / then — nested path passes the relaxed shape check
        assertThatCode(() -> new PrTrackingProps(
                        true,
                        "0 0 9-18 * * 1-5",
                        "pr",
                        List.of("tag"),
                        "low",
                        DEFAULT_DURATION_UNIT,
                        List.of(repo),
                        validTokenGithub(),
                        new PrTrackingProps.Gitlab("https://gitlab.com/api/v4", "glpat-123"),
                        DEFAULT_SLA_DISCOVERY))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsGitLabRepoNameWithSlashButBlankSegment() {
        PrTrackingProps.Repository badName = new PrTrackingProps.Repository(
                "group//project",
                "wow",
                Provider.GITLAB,
                null,
                "group/reviewers",
                List.of(),
                sla(Duration.ofDays(2)),
                null,
                null);

        assertThatThrownBy(() -> new PrTrackingProps(
                        true,
                        "0 0 9-18 * * 1-5",
                        "pr",
                        List.of("tag"),
                        "low",
                        DEFAULT_DURATION_UNIT,
                        List.of(badName),
                        validTokenGithub(),
                        new PrTrackingProps.Gitlab("https://gitlab.com/api/v4", "glpat-123"),
                        DEFAULT_SLA_DISCOVERY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must contain at least one '/'");
    }

    @Test
    void rejectsGitLabRepoNameWithSingleSegmentAtPropsLevel() {
        PrTrackingProps.Repository badName = new PrTrackingProps.Repository(
                "no-slash",
                "wow",
                Provider.GITLAB,
                null,
                "group/reviewers",
                List.of(),
                sla(Duration.ofDays(2)),
                null,
                null);

        assertThatThrownBy(() -> new PrTrackingProps(
                        true,
                        "0 0 9-18 * * 1-5",
                        "pr",
                        List.of("tag"),
                        "low",
                        DEFAULT_DURATION_UNIT,
                        List.of(badName),
                        validTokenGithub(),
                        new PrTrackingProps.Gitlab("https://gitlab.com/api/v4", "glpat-123"),
                        DEFAULT_SLA_DISCOVERY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must contain at least one '/'");
    }

    @Test
    void rejectsGithubTeamSlugOnGitLabRepo() {
        assertThatThrownBy(() -> new PrTrackingProps.Repository(
                        "my-group/project",
                        "wow",
                        Provider.GITLAB,
                        "github-team-slug",
                        null,
                        List.of(),
                        sla(Duration.ofDays(2)),
                        null,
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("githubTeamSlug is only valid when provider=github");
    }

    @Test
    void rejectsGitlabGroupPathOnGithubRepo() {
        assertThatThrownBy(() -> new PrTrackingProps.Repository(
                        "my-org/repo",
                        "wow",
                        Provider.GITHUB,
                        null,
                        "my-group/reviewers",
                        List.of(),
                        sla(Duration.ofDays(2)),
                        null,
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gitlabGroupPath is only valid when provider=gitlab");
    }

    @Test
    void rejectsPerRepoGitlabOverrideOnGithubRepo() {
        PrTrackingProps.Gitlab gitlabBlock = new PrTrackingProps.Gitlab("https://gitlab.com/api/v4", "glpat-xxx");
        assertThatThrownBy(() -> new PrTrackingProps.Repository(
                        "my-org/repo",
                        "wow",
                        Provider.GITHUB,
                        null,
                        null,
                        List.of(),
                        sla(Duration.ofDays(2)),
                        gitlabBlock,
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("per-repo gitlab override block is only valid when provider=gitlab");
    }

    @Test
    void rejectsBlankGitlabGroupPath() {
        assertThatThrownBy(() -> new PrTrackingProps.Repository(
                        "my-group/project",
                        "wow",
                        Provider.GITLAB,
                        null,
                        "  ",
                        List.of(),
                        sla(Duration.ofDays(2)),
                        null,
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gitlabGroupPath must not be blank when provided");
    }

    @Test
    void rejectsGitLabRepoWhenNoTokenResolvable() {
        // given — gitlab repo without per-repo override, and no global gitlab block at all
        PrTrackingProps.Repository repo = gitlabRepo("my-group/project");

        assertThatThrownBy(() -> new PrTrackingProps(
                        true,
                        "0 0 9-18 * * 1-5",
                        "pr",
                        List.of("tag"),
                        "low",
                        DEFAULT_DURATION_UNIT,
                        List.of(repo),
                        validTokenGithub(),
                        null,
                        DEFAULT_SLA_DISCOVERY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gitlab.token must be set");
    }

    @Test
    void rejectsGitLabRepoWhenGlobalGitlabHasBlankToken() {
        PrTrackingProps.Repository repo = gitlabRepo("my-group/project");

        assertThatThrownBy(() -> new PrTrackingProps(
                        true,
                        "0 0 9-18 * * 1-5",
                        "pr",
                        List.of("tag"),
                        "low",
                        DEFAULT_DURATION_UNIT,
                        List.of(repo),
                        validTokenGithub(),
                        new PrTrackingProps.Gitlab("https://gitlab.com/api/v4", ""),
                        DEFAULT_SLA_DISCOVERY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("gitlab.token must be set");
    }

    @Test
    void acceptsGitLabOnlyDeploymentWithoutGithubBlock() {
        // given — pure-GitLab config: gitlab repo + gitlab block, github block omitted entirely
        PrTrackingProps.Repository repo = gitlabRepo("my-group/project");

        // when / then — startup succeeds; the github validator is silent because no github repos exist
        assertThatCode(() -> new PrTrackingProps(
                        true,
                        "0 0 9-18 * * 1-5",
                        "pr",
                        List.of("tag"),
                        "low",
                        DEFAULT_DURATION_UNIT,
                        List.of(repo),
                        null,
                        new PrTrackingProps.Gitlab("https://gitlab.com/api/v4", "glpat-123"),
                        DEFAULT_SLA_DISCOVERY))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsMixedDeploymentWithBothProviders() {
        // given — one github repo and one gitlab repo
        PrTrackingProps.Repository ghRepo = validRepo();
        PrTrackingProps.Repository glRepo = gitlabRepo("my-group/project");

        // when / then — both top-level blocks present, validation passes
        assertThatCode(() -> new PrTrackingProps(
                        true,
                        "0 0 9-18 * * 1-5",
                        "pr",
                        List.of("tag"),
                        "low",
                        DEFAULT_DURATION_UNIT,
                        List.of(ghRepo, glRepo),
                        validTokenGithub(),
                        new PrTrackingProps.Gitlab("https://gitlab.com/api/v4", "glpat-123"),
                        DEFAULT_SLA_DISCOVERY))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsGitLabRepoWithPerRepoTokenAndNoGlobal() {
        // given — global gitlab block omitted; per-repo override carries the token
        PrTrackingProps.Repository repo = new PrTrackingProps.Repository(
                "my-group/project",
                "wow",
                Provider.GITLAB,
                null,
                "my-group/reviewers",
                List.of(),
                sla(Duration.ofDays(2)),
                new PrTrackingProps.Gitlab("https://gitlab.internal.example.com/api/v4", "glpat-internal"),
                null);

        assertThatCode(() -> new PrTrackingProps(
                        true,
                        "0 0 9-18 * * 1-5",
                        "pr",
                        List.of("tag"),
                        "low",
                        DEFAULT_DURATION_UNIT,
                        List.of(repo),
                        validTokenGithub(),
                        null,
                        DEFAULT_SLA_DISCOVERY))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsGitlabApiBaseUrlWithTrailingSlash() {
        assertThatThrownBy(() -> new PrTrackingProps.Gitlab("https://gitlab.com/api/v4/", "glpat-123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not end with a trailing slash");
    }

    @Test
    void rejectsGitlabApiBaseUrlWithoutApiV4Segment() {
        assertThatThrownBy(() -> new PrTrackingProps.Gitlab("https://gitlab.com", "glpat-123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must include the /api/v4 segment");
    }

    @Test
    void acceptsGitlabApiBaseUrlWithSelfHostedDomain() {
        assertThatCode(() -> new PrTrackingProps.Gitlab("https://gitlab.internal.example.com/api/v4", "glpat-123"))
                .doesNotThrowAnyException();
    }

    @Test
    void gitlabTokenIsRedactedInToString() {
        // Sanity check: don't surface the token in logs even if a config dump is wired up later.
        PrTrackingProps.Gitlab gitlab = new PrTrackingProps.Gitlab("https://gitlab.com/api/v4", "glpat-secret");
        assertThat(gitlab.toString()).contains("REDACTED").doesNotContain("glpat-secret");
    }

    @Test
    void detectsDuplicateRepositoryNameAcrossProvidersIgnoringCase() {
        // The unique constraint on pr_tracking already keys on (ticket_id, provider, repo, pr_number)
        // so cross-provider collisions are technically allowed by the schema — but in YAML it almost
        // certainly indicates a misconfig (two entries for what the operator thinks is the same repo).
        // Keep the current strict check: dedupe on the lowercased name regardless of provider.
        PrTrackingProps.Repository ghRepo = validRepoWithName("acme/widget");
        PrTrackingProps.Repository glRepo = gitlabRepo("acme/widget");

        assertThatThrownBy(() -> new PrTrackingProps(
                        true,
                        "0 0 9-18 * * 1-5",
                        "pr",
                        List.of("tag"),
                        "low",
                        DEFAULT_DURATION_UNIT,
                        List.of(ghRepo, glRepo),
                        validTokenGithub(),
                        new PrTrackingProps.Gitlab("https://gitlab.com/api/v4", "glpat-123"),
                        DEFAULT_SLA_DISCOVERY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contains duplicates");
    }

    private static PrTrackingProps.Repository gitlabRepo(String name) {
        return new PrTrackingProps.Repository(
                name,
                "wow",
                Provider.GITLAB,
                null,
                "my-group/reviewers",
                List.of(),
                sla(Duration.ofDays(2)),
                null,
                null);
    }

    // ---- helpers --------------------------------------------------------------------------------

    private static PrTrackingProps.Repository validRepo() {
        return validRepoWithName("my-org/onboarding-repo");
    }

    private static PrTrackingProps.Repository validRepoWithName(String name) {
        return new PrTrackingProps.Repository(name, "wow", null, List.of(), sla(Duration.ofDays(2)));
    }

    private static PrTrackingProps.Sla sla(Duration defaultSla) {
        return new PrTrackingProps.Sla(null, defaultSla, null);
    }

    private static PrTrackingProps.GitHub validTokenGithub() {
        return new PrTrackingProps.GitHub(
                PrTrackingProps.AuthMode.TOKEN, "https://api.github.com", "pat-123", "", "", "");
    }
}
