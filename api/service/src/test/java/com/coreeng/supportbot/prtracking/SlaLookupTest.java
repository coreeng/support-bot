package com.coreeng.supportbot.prtracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.config.PrTrackingProps;
import com.coreeng.supportbot.prtracking.source.PrSourceClient;
import com.coreeng.supportbot.prtracking.source.PrSourceClients;
import com.coreeng.supportbot.prtracking.source.PrSourceException;
import com.coreeng.supportbot.prtracking.source.Provider;
import com.coreeng.supportbot.prtracking.source.RepoCoord;
import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SlaLookupTest {

    private static final String REPO = "my-org/my-repo";
    private static final RepoCoord COORD = RepoCoord.github(REPO);
    private static final int PR_NUMBER = 42;
    private static final Duration SLA_48H = Duration.ofDays(2);
    private static final Duration SLA_72H = Duration.ofDays(3);
    private static final Duration SLA_24H = Duration.ofDays(1);

    @Mock
    private PrSourceClients prSourceClients;

    @Mock
    private PrSourceClient prSourceClient;

    private SlaLookup slaLookup;

    @BeforeEach
    void setUp() {
        lenient().when(prSourceClients.forProvider(Provider.GITHUB)).thenReturn(prSourceClient);
        PrTrackingProps props = new PrTrackingProps(
                true,
                "0 0 9-18 * * 1-5",
                "pr",
                List.of("tag"),
                "low",
                "days",
                List.of(new PrTrackingProps.Repository(
                        REPO, "wow", null, List.of(), new PrTrackingProps.Sla(null, SLA_48H, null))),
                new PrTrackingProps.GitHub(
                        PrTrackingProps.AuthMode.TOKEN, "https://api.github.com", "token", "", "", ""),
                null);
        slaLookup = new SlaLookup(prSourceClients, props);
    }

    @Test
    void returnsOverrideSlaWhenPrFileMatchesPattern() {
        // given
        PrTrackingProps.Repository repoConfig =
                repoWithOverrides(SLA_48H, List.of(new PrTrackingProps.SlaOverride("docs/**", SLA_72H)));
        when(prSourceClient.listChangedFiles(COORD, PR_NUMBER)).thenReturn(List.of("docs/README.md"));

        // when
        Duration result = slaLookup.getSla(repoConfig, COORD, PR_NUMBER);

        // then
        assertThat(result).isEqualTo(SLA_72H);
    }

    @Test
    void matchesTrailingSlashOverrideAsRecursiveGlob() {
        // given an override path ending with "/" e.g. "docs/"
        PrTrackingProps.Repository repoConfig =
                repoWithOverrides(SLA_48H, List.of(new PrTrackingProps.SlaOverride("docs/", SLA_72H)));
        when(prSourceClient.listChangedFiles(COORD, PR_NUMBER)).thenReturn(List.of("docs/guides/setup.md"));

        // when the SLA is resolved for a PR touching a nested file
        Duration result = slaLookup.getSla(repoConfig, COORD, PR_NUMBER);

        // then the trailing slash is treated as "docs/**" and matches recursively
        assertThat(result).isEqualTo(SLA_72H);
    }

    @Test
    void returnsDefaultSlaWhenNoOverrideMatches() {
        // given
        PrTrackingProps.Repository repoConfig =
                repoWithOverrides(SLA_48H, List.of(new PrTrackingProps.SlaOverride("docs/**", SLA_72H)));
        when(prSourceClient.listChangedFiles(COORD, PR_NUMBER)).thenReturn(List.of("src/Main.java"));

        // when
        Duration result = slaLookup.getSla(repoConfig, COORD, PR_NUMBER);

        // then
        assertThat(result).isEqualTo(SLA_48H);
    }

    @Test
    void firstMatchingOverrideWins() {
        // given
        PrTrackingProps.Repository repoConfig = repoWithOverrides(
                SLA_48H,
                List.of(
                        new PrTrackingProps.SlaOverride("src/**", SLA_24H),
                        new PrTrackingProps.SlaOverride("src/critical/**", SLA_72H)));
        when(prSourceClient.listChangedFiles(COORD, PR_NUMBER)).thenReturn(List.of("src/critical/App.java"));

        // when
        Duration result = slaLookup.getSla(repoConfig, COORD, PR_NUMBER);

        // then — src/** matches first, even though src/critical/** is more specific
        assertThat(result).isEqualTo(SLA_24H);
    }

    @Test
    void usesFileDefaultWhenSlaFileExists() {
        // given
        PrTrackingProps.Repository repoConfig = repoWithFile(".pr-sla.yaml", SLA_48H);
        when(prSourceClient.fetchFileContents(COORD, ".pr-sla.yaml")).thenReturn("default: 72h");

        // when
        Duration result = slaLookup.getSla(repoConfig, COORD, PR_NUMBER);

        // then
        assertThat(result).isEqualTo(SLA_72H);
    }

    @Test
    void usesFileOverridesWhenSlaFileExists() {
        // given
        PrTrackingProps.Repository repoConfig = repoWithFile(".pr-sla.yaml", SLA_48H);
        when(prSourceClient.fetchFileContents(COORD, ".pr-sla.yaml"))
                .thenReturn("default: 48h\noverrides:\n  - path: \"docs/**\"\n    sla: 72h");
        when(prSourceClient.listChangedFiles(COORD, PR_NUMBER)).thenReturn(List.of("docs/guide.md"));

        // when
        Duration result = slaLookup.getSla(repoConfig, COORD, PR_NUMBER);

        // then
        assertThat(result).isEqualTo(SLA_72H);
    }

    @Test
    void fallsBackToConfigDefaultWhenFileNotFound() {
        // given
        PrTrackingProps.Repository repoConfig = repoWithFile(".pr-sla.yaml", SLA_48H);
        when(prSourceClient.fetchFileContents(COORD, ".pr-sla.yaml")).thenReturn(null);

        // when
        Duration result = slaLookup.getSla(repoConfig, COORD, PR_NUMBER);

        // then
        assertThat(result).isEqualTo(SLA_48H);
    }

    @Test
    void returnsNullWhenFileNotFoundAndNoConfigDefault() {
        // given
        PrTrackingProps.Repository repoConfig = repoWithFile(".pr-sla.yaml", null);
        when(prSourceClient.fetchFileContents(COORD, ".pr-sla.yaml")).thenReturn(null);

        // when
        Duration result = slaLookup.getSla(repoConfig, COORD, PR_NUMBER);

        // then
        assertThat(result).isNull();
    }

    @Test
    void fallsBackToConfigDefaultOnYamlParseError() {
        // given
        PrTrackingProps.Repository repoConfig = repoWithFile(".pr-sla.yaml", SLA_48H);
        when(prSourceClient.fetchFileContents(COORD, ".pr-sla.yaml"))
                .thenReturn("not: [valid: yaml: {{{")
                .thenReturn("default: 72h");

        // when — first call has invalid YAML, falls back to config default
        Duration result = slaLookup.getSla(repoConfig, COORD, PR_NUMBER);
        assertThat(result).isEqualTo(SLA_48H);

        // then — parse error is NOT cached, second call retries and picks up the fix
        Duration retryResult = slaLookup.getSla(repoConfig, COORD, PR_NUMBER);
        assertThat(retryResult).isEqualTo(SLA_72H);
        verify(prSourceClient, times(2)).fetchFileContents(COORD, ".pr-sla.yaml");
    }

    @Test
    void cachesFileContentBetweenCalls() {
        // given
        PrTrackingProps.Repository repoConfig = repoWithFile(".pr-sla.yaml", SLA_48H);
        when(prSourceClient.fetchFileContents(COORD, ".pr-sla.yaml")).thenReturn("default: 72h");

        // when
        slaLookup.getSla(repoConfig, COORD, PR_NUMBER);
        slaLookup.getSla(repoConfig, COORD, PR_NUMBER);

        // then — only one fetch, second call used cache
        verify(prSourceClient, times(1)).fetchFileContents(COORD, ".pr-sla.yaml");
    }

    @Test
    void propagatesPrSourceExceptionWithoutCaching() {
        // given
        PrTrackingProps.Repository repoConfig = repoWithFile(".pr-sla.yaml", SLA_48H);
        when(prSourceClient.fetchFileContents(COORD, ".pr-sla.yaml"))
                .thenThrow(new PrSourceException("server error"))
                .thenReturn("default: 72h");

        // when, first call throws
        org.junit.jupiter.api.Assertions.assertThrows(
                PrSourceException.class, () -> slaLookup.getSla(repoConfig, COORD, PR_NUMBER));

        // then, second call retries and succeeds (error was not cached)
        Duration result = slaLookup.getSla(repoConfig, COORD, PR_NUMBER);
        assertThat(result).isEqualTo(SLA_72H);
        verify(prSourceClient, times(2)).fetchFileContents(COORD, ".pr-sla.yaml");
    }

    @Test
    void cachesFileNotFoundBetweenCalls() {
        // given
        PrTrackingProps.Repository repoConfig = repoWithFile(".pr-sla.yaml", SLA_48H);
        when(prSourceClient.fetchFileContents(COORD, ".pr-sla.yaml")).thenReturn(null);

        // when
        slaLookup.getSla(repoConfig, COORD, PR_NUMBER);
        slaLookup.getSla(repoConfig, COORD, PR_NUMBER);

        // then only one fetch, the 404 result is cached
        verify(prSourceClient, times(1)).fetchFileContents(COORD, ".pr-sla.yaml");
    }

    @Test
    void fallsBackToConfigDefaultOnInvalidDurationInFile() {
        // given
        PrTrackingProps.Repository repoConfig = repoWithFile(".pr-sla.yaml", SLA_48H);
        when(prSourceClient.fetchFileContents(COORD, ".pr-sla.yaml"))
                .thenReturn("default: forever")
                .thenReturn("default: 72h");

        // when — first call has invalid file, falls back to config default
        Duration result = slaLookup.getSla(repoConfig, COORD, PR_NUMBER);
        assertThat(result).isEqualTo(SLA_48H);

        // then — invalid file is NOT cached, second call retries and picks up the fix
        Duration retryResult = slaLookup.getSla(repoConfig, COORD, PR_NUMBER);
        assertThat(retryResult).isEqualTo(SLA_72H);
        verify(prSourceClient, times(2)).fetchFileContents(COORD, ".pr-sla.yaml");
    }

    @Test
    void parsesDayDurationFromFile() {
        // given
        PrTrackingProps.Repository repoConfig = repoWithFile(".pr-sla.yaml", SLA_48H);
        when(prSourceClient.fetchFileContents(COORD, ".pr-sla.yaml")).thenReturn("default: 7d");

        // when
        Duration result = slaLookup.getSla(repoConfig, COORD, PR_NUMBER);

        // then
        assertThat(result).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void parsesIsoDurationFromFile() {
        // given
        PrTrackingProps.Repository repoConfig = repoWithFile(".pr-sla.yaml", SLA_48H);
        when(prSourceClient.fetchFileContents(COORD, ".pr-sla.yaml")).thenReturn("default: P14D");

        // when
        Duration result = slaLookup.getSla(repoConfig, COORD, PR_NUMBER);

        // then
        assertThat(result).isEqualTo(Duration.ofDays(14));
    }

    @Test
    void fallsBackToConfigWhenOverrideEntryMissingPath() {
        // given
        PrTrackingProps.Repository repoConfig = repoWithFile(".pr-sla.yaml", SLA_48H);
        when(prSourceClient.fetchFileContents(COORD, ".pr-sla.yaml"))
                .thenReturn("default: 72h\noverrides:\n  - sla: 24h")
                .thenReturn("default: 72h");

        // when, first call has invalid override (missing path), falls back to config default
        Duration result = slaLookup.getSla(repoConfig, COORD, PR_NUMBER);
        assertThat(result).isEqualTo(SLA_48H);

        // then, invalid file is NOT cached, second call retries and picks up the fix
        Duration retryResult = slaLookup.getSla(repoConfig, COORD, PR_NUMBER);
        assertThat(retryResult).isEqualTo(SLA_72H);
        verify(prSourceClient, times(2)).fetchFileContents(COORD, ".pr-sla.yaml");
    }

    @Test
    void fallsBackToConfigWhenOverrideEntryMissingSla() {
        // given
        PrTrackingProps.Repository repoConfig = repoWithFile(".pr-sla.yaml", SLA_48H);
        when(prSourceClient.fetchFileContents(COORD, ".pr-sla.yaml"))
                .thenReturn("default: 72h\noverrides:\n  - path: \"docs/**\"")
                .thenReturn("default: 72h");

        // when, first call has invalid override (missing sla), falls back to config default
        Duration result = slaLookup.getSla(repoConfig, COORD, PR_NUMBER);
        assertThat(result).isEqualTo(SLA_48H);

        // then, invalid file is NOT cached, second call retries and picks up the fix
        Duration retryResult = slaLookup.getSla(repoConfig, COORD, PR_NUMBER);
        assertThat(retryResult).isEqualTo(SLA_72H);
        verify(prSourceClient, times(2)).fetchFileContents(COORD, ".pr-sla.yaml");
    }

    @Test
    void fileOverridesReplaceConfigOverridesButConfigDefaultPreserved() {
        // given, file has overrides but no default, config has default but no overrides
        PrTrackingProps.Repository repoConfig = repoWithFile(".pr-sla.yaml", SLA_48H);
        when(prSourceClient.fetchFileContents(COORD, ".pr-sla.yaml"))
                .thenReturn("overrides:\n  - path: \"docs/**\"\n    sla: 24h");
        when(prSourceClient.listChangedFiles(COORD, PR_NUMBER)).thenReturn(List.of("docs/README.md"));

        // when
        Duration result = slaLookup.getSla(repoConfig, COORD, PR_NUMBER);

        // then, file override matched, config default (48h) preserved but not used
        assertThat(result).isEqualTo(SLA_24H);
    }

    @Test
    void fileWithOverridesButNoDefaultFallsBackToConfigDefault() {
        // given, file has overrides but no default, PR files don't match any override
        PrTrackingProps.Repository repoConfig = repoWithFile(".pr-sla.yaml", SLA_48H);
        when(prSourceClient.fetchFileContents(COORD, ".pr-sla.yaml"))
                .thenReturn("overrides:\n  - path: \"docs/**\"\n    sla: 24h");
        when(prSourceClient.listChangedFiles(COORD, PR_NUMBER)).thenReturn(List.of("src/Main.java"));

        // when
        Duration result = slaLookup.getSla(repoConfig, COORD, PR_NUMBER);

        // then, no override matched, config default (48h) used
        assertThat(result).isEqualTo(SLA_48H);
    }

    @Test
    void parsesBareIntegerAsDaysFromFile() {
        PrTrackingProps.Repository repoConfig = repoWithFile(".pr-sla.yaml", SLA_48H);
        when(prSourceClient.fetchFileContents(COORD, ".pr-sla.yaml")).thenReturn("default: 2");

        Duration result = slaLookup.getSla(repoConfig, COORD, PR_NUMBER);

        assertThat(result).isEqualTo(Duration.ofDays(2));
    }

    @Test
    void parsesDaySuffixFromFile() {
        PrTrackingProps.Repository repoConfig = repoWithFile(".pr-sla.yaml", SLA_48H);
        when(prSourceClient.fetchFileContents(COORD, ".pr-sla.yaml")).thenReturn("default: 2d");

        Duration result = slaLookup.getSla(repoConfig, COORD, PR_NUMBER);

        assertThat(result).isEqualTo(Duration.ofDays(2));
    }

    @Test
    void parsesBareIntegerInOverrideFromFile() {
        PrTrackingProps.Repository repoConfig = repoWithFile(".pr-sla.yaml", SLA_48H);
        when(prSourceClient.fetchFileContents(COORD, ".pr-sla.yaml"))
                .thenReturn("default: 2\noverrides:\n  - path: \"docs/**\"\n    sla: 5");
        when(prSourceClient.listChangedFiles(COORD, PR_NUMBER)).thenReturn(List.of("docs/README.md"));

        Duration result = slaLookup.getSla(repoConfig, COORD, PR_NUMBER);

        assertThat(result).isEqualTo(Duration.ofDays(5));
    }

    @Test
    void parsesBareIntegerAsHoursWhenDurationUnitIsHours() {
        // given duration-unit is configured as "hours"
        SlaLookup hoursLookup = slaLookupWithDurationUnit("hours");
        PrTrackingProps.Repository repoConfig = repoWithFile(".pr-sla.yaml", null);
        when(prSourceClient.fetchFileContents(COORD, ".pr-sla.yaml")).thenReturn("default: 4");

        // when a bare integer SLA is parsed from the file
        Duration result = hoursLookup.getSla(repoConfig, COORD, PR_NUMBER);

        // then it is interpreted as hours
        assertThat(result).isEqualTo(Duration.ofHours(4));
    }

    @Test
    void parsesBareIntegerAsWeeksWhenDurationUnitIsWeeks() {
        // given duration-unit is configured as "weeks"
        SlaLookup weeksLookup = slaLookupWithDurationUnit("weeks");
        PrTrackingProps.Repository repoConfig = repoWithFile(".pr-sla.yaml", null);
        when(prSourceClient.fetchFileContents(COORD, ".pr-sla.yaml")).thenReturn("default: 2");

        // when a bare integer SLA is parsed from the file
        Duration result = weeksLookup.getSla(repoConfig, COORD, PR_NUMBER);

        // then it is interpreted as weeks (14 days)
        assertThat(result).isEqualTo(Duration.ofDays(14));
    }

    @Test
    void parsesWeekSuffixFromFile() {
        // given an SLA file with a week suffix e.g. "1w"
        PrTrackingProps.Repository repoConfig = repoWithFile(".pr-sla.yaml", null);
        when(prSourceClient.fetchFileContents(COORD, ".pr-sla.yaml")).thenReturn("default: 1w");

        // when the SLA is resolved from the file
        Duration result = slaLookup.getSla(repoConfig, COORD, PR_NUMBER);

        // then it is parsed as 7 days
        assertThat(result).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void rejectsZeroBareIntegerAndFallsBackToConfig() {
        // given an SLA file with a zero bare integer
        PrTrackingProps.Repository repoConfig = repoWithFile(".pr-sla.yaml", SLA_48H);
        when(prSourceClient.fetchFileContents(COORD, ".pr-sla.yaml")).thenReturn("default: 0");

        // when the SLA is resolved from the file
        Duration result = slaLookup.getSla(repoConfig, COORD, PR_NUMBER);

        // then the zero value is rejected and config default is used
        assertThat(result).isEqualTo(SLA_48H);
    }

    @Test
    void suffixedDurationIsNotAffectedByDurationUnit() {
        // given duration-unit is configured as "weeks" but the file uses an explicit "48h" suffix
        SlaLookup weeksLookup = slaLookupWithDurationUnit("weeks");
        PrTrackingProps.Repository repoConfig = repoWithFile(".pr-sla.yaml", null);
        when(prSourceClient.fetchFileContents(COORD, ".pr-sla.yaml")).thenReturn("default: 48h");

        // when the SLA is resolved from the file
        Duration result = weeksLookup.getSla(repoConfig, COORD, PR_NUMBER);

        // then the suffix takes precedence over the configured duration-unit
        @SuppressWarnings("CanonicalDuration") // intentionally using hours to assert the unit
        Duration expected = Duration.ofHours(48);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void returnsDefaultSlaWhenNoOverridesConfigured() {
        // given
        PrTrackingProps.Repository repoConfig = repo(SLA_48H);

        // when
        Duration result = slaLookup.getSla(repoConfig, COORD, PR_NUMBER);

        // then
        assertThat(result).isEqualTo(SLA_48H);
        verify(prSourceClient, never()).listChangedFiles(any(), anyInt());
    }

    private SlaLookup slaLookupWithDurationUnit(String durationUnit) {
        PrTrackingProps props = new PrTrackingProps(
                true,
                "0 0 9-18 * * 1-5",
                "pr",
                List.of("tag"),
                "low",
                durationUnit,
                List.of(new PrTrackingProps.Repository(
                        REPO, "wow", null, List.of(), new PrTrackingProps.Sla(null, SLA_48H, null))),
                new PrTrackingProps.GitHub(
                        PrTrackingProps.AuthMode.TOKEN, "https://api.github.com", "token", "", "", ""),
                null);
        return new SlaLookup(prSourceClients, props);
    }

    private static PrTrackingProps.Repository repo(Duration defaultSla) {
        return new PrTrackingProps.Repository(
                REPO, "wow", null, List.of(), new PrTrackingProps.Sla(null, defaultSla, null));
    }

    private static PrTrackingProps.Repository repoWithOverrides(
            Duration defaultSla, List<PrTrackingProps.SlaOverride> overrides) {
        return new PrTrackingProps.Repository(
                REPO, "wow", null, List.of(), new PrTrackingProps.Sla(null, defaultSla, overrides));
    }

    private static PrTrackingProps.Repository repoWithFile(String file, @Nullable Duration defaultSla) {
        return new PrTrackingProps.Repository(
                REPO, "wow", null, List.of(), new PrTrackingProps.Sla(file, defaultSla, null));
    }
}
