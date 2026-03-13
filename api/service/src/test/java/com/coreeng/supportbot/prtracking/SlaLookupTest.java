package com.coreeng.supportbot.prtracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.coreeng.supportbot.config.PrTrackingProps;
import com.coreeng.supportbot.github.GitHubClient;
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
    private static final int PR_NUMBER = 42;
    private static final Duration SLA_48H = Duration.ofDays(2);
    private static final Duration SLA_72H = Duration.ofDays(3);
    private static final Duration SLA_24H = Duration.ofDays(1);

    @Mock
    private GitHubClient gitHubClient;

    private SlaLookup slaLookup;

    @BeforeEach
    void setUp() {
        PrTrackingProps props = new PrTrackingProps(
                true,
                "0 0 9-18 * * 1-5",
                "pr",
                List.of("tag"),
                "low",
                List.of(new PrTrackingProps.Repository(REPO, "wow", new PrTrackingProps.Sla(null, SLA_48H, null))),
                new PrTrackingProps.GitHub(
                        PrTrackingProps.AuthMode.TOKEN, "https://api.github.com", "token", "", "", ""),
                null);
        slaLookup = new SlaLookup(gitHubClient, props);
    }

    @Test
    void returnsOverrideSlaWhenPrFileMatchesPattern() {
        // given
        PrTrackingProps.Repository repoConfig =
                repoWithOverrides(SLA_48H, List.of(new PrTrackingProps.SlaOverride("docs/**", SLA_72H)));
        when(gitHubClient.listPullRequestFiles(REPO, PR_NUMBER)).thenReturn(List.of("docs/README.md"));

        // when
        Duration result = slaLookup.getSla(repoConfig, REPO, PR_NUMBER);

        // then
        assertThat(result).isEqualTo(SLA_72H);
    }

    @Test
    void returnsDefaultSlaWhenNoOverrideMatches() {
        // given
        PrTrackingProps.Repository repoConfig =
                repoWithOverrides(SLA_48H, List.of(new PrTrackingProps.SlaOverride("docs/**", SLA_72H)));
        when(gitHubClient.listPullRequestFiles(REPO, PR_NUMBER)).thenReturn(List.of("src/Main.java"));

        // when
        Duration result = slaLookup.getSla(repoConfig, REPO, PR_NUMBER);

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
        when(gitHubClient.listPullRequestFiles(REPO, PR_NUMBER)).thenReturn(List.of("src/critical/App.java"));

        // when
        Duration result = slaLookup.getSla(repoConfig, REPO, PR_NUMBER);

        // then — src/** matches first, even though src/critical/** is more specific
        assertThat(result).isEqualTo(SLA_24H);
    }

    @Test
    void usesFileDefaultWhenSlaFileExists() {
        // given
        PrTrackingProps.Repository repoConfig = repoWithFile(".pr-sla.yaml", SLA_48H);
        when(gitHubClient.getFileContent(REPO, ".pr-sla.yaml")).thenReturn("default: 72h");

        // when
        Duration result = slaLookup.getSla(repoConfig, REPO, PR_NUMBER);

        // then
        assertThat(result).isEqualTo(SLA_72H);
    }

    @Test
    void usesFileOverridesWhenSlaFileExists() {
        // given
        PrTrackingProps.Repository repoConfig = repoWithFile(".pr-sla.yaml", SLA_48H);
        when(gitHubClient.getFileContent(REPO, ".pr-sla.yaml"))
                .thenReturn("default: 48h\noverrides:\n  - path: \"docs/**\"\n    sla: 72h");
        when(gitHubClient.listPullRequestFiles(REPO, PR_NUMBER)).thenReturn(List.of("docs/guide.md"));

        // when
        Duration result = slaLookup.getSla(repoConfig, REPO, PR_NUMBER);

        // then
        assertThat(result).isEqualTo(SLA_72H);
    }

    @Test
    void fallsBackToConfigDefaultWhenFileNotFound() {
        // given
        PrTrackingProps.Repository repoConfig = repoWithFile(".pr-sla.yaml", SLA_48H);
        when(gitHubClient.getFileContent(REPO, ".pr-sla.yaml")).thenReturn(null);

        // when
        Duration result = slaLookup.getSla(repoConfig, REPO, PR_NUMBER);

        // then
        assertThat(result).isEqualTo(SLA_48H);
    }

    @Test
    void returnsNullWhenFileNotFoundAndNoConfigDefault() {
        // given
        PrTrackingProps.Repository repoConfig = repoWithFile(".pr-sla.yaml", null);
        when(gitHubClient.getFileContent(REPO, ".pr-sla.yaml")).thenReturn(null);

        // when
        Duration result = slaLookup.getSla(repoConfig, REPO, PR_NUMBER);

        // then
        assertThat(result).isNull();
    }

    @Test
    void fallsBackToConfigDefaultOnYamlParseError() {
        // given
        PrTrackingProps.Repository repoConfig = repoWithFile(".pr-sla.yaml", SLA_48H);
        when(gitHubClient.getFileContent(REPO, ".pr-sla.yaml"))
                .thenReturn("not: [valid: yaml: {{{")
                .thenReturn("default: 72h");

        // when — first call has invalid YAML, falls back to config default
        Duration result = slaLookup.getSla(repoConfig, REPO, PR_NUMBER);
        assertThat(result).isEqualTo(SLA_48H);

        // then — parse error is NOT cached, second call retries and picks up the fix
        Duration retryResult = slaLookup.getSla(repoConfig, REPO, PR_NUMBER);
        assertThat(retryResult).isEqualTo(SLA_72H);
        verify(gitHubClient, times(2)).getFileContent(REPO, ".pr-sla.yaml");
    }

    @Test
    void cachesFileContentBetweenCalls() {
        // given
        PrTrackingProps.Repository repoConfig = repoWithFile(".pr-sla.yaml", SLA_48H);
        when(gitHubClient.getFileContent(REPO, ".pr-sla.yaml")).thenReturn("default: 72h");

        // when
        slaLookup.getSla(repoConfig, REPO, PR_NUMBER);
        slaLookup.getSla(repoConfig, REPO, PR_NUMBER);

        // then — only one fetch, second call used cache
        verify(gitHubClient, times(1)).getFileContent(REPO, ".pr-sla.yaml");
    }

    @Test
    void propagatesGitHubApiExceptionWithoutCaching() {
        // given
        PrTrackingProps.Repository repoConfig = repoWithFile(".pr-sla.yaml", SLA_48H);
        when(gitHubClient.getFileContent(REPO, ".pr-sla.yaml"))
                .thenThrow(new com.coreeng.supportbot.github.GitHubApiException(500, "server error"))
                .thenReturn("default: 72h");

        // when, first call throws
        org.junit.jupiter.api.Assertions.assertThrows(
                com.coreeng.supportbot.github.GitHubApiException.class,
                () -> slaLookup.getSla(repoConfig, REPO, PR_NUMBER));

        // then, second call retries and succeeds (error was not cached)
        Duration result = slaLookup.getSla(repoConfig, REPO, PR_NUMBER);
        assertThat(result).isEqualTo(SLA_72H);
        verify(gitHubClient, times(2)).getFileContent(REPO, ".pr-sla.yaml");
    }

    @Test
    void cachesFileNotFoundBetweenCalls() {
        // given
        PrTrackingProps.Repository repoConfig = repoWithFile(".pr-sla.yaml", SLA_48H);
        when(gitHubClient.getFileContent(REPO, ".pr-sla.yaml")).thenReturn(null);

        // when
        slaLookup.getSla(repoConfig, REPO, PR_NUMBER);
        slaLookup.getSla(repoConfig, REPO, PR_NUMBER);

        // then only one fetch, the 404 result is cached
        verify(gitHubClient, times(1)).getFileContent(REPO, ".pr-sla.yaml");
    }

    @Test
    void fallsBackToConfigDefaultOnInvalidDurationInFile() {
        // given
        PrTrackingProps.Repository repoConfig = repoWithFile(".pr-sla.yaml", SLA_48H);
        when(gitHubClient.getFileContent(REPO, ".pr-sla.yaml"))
                .thenReturn("default: forever")
                .thenReturn("default: 72h");

        // when — first call has invalid file, falls back to config default
        Duration result = slaLookup.getSla(repoConfig, REPO, PR_NUMBER);
        assertThat(result).isEqualTo(SLA_48H);

        // then — invalid file is NOT cached, second call retries and picks up the fix
        Duration retryResult = slaLookup.getSla(repoConfig, REPO, PR_NUMBER);
        assertThat(retryResult).isEqualTo(SLA_72H);
        verify(gitHubClient, times(2)).getFileContent(REPO, ".pr-sla.yaml");
    }

    @Test
    void parsesWeekDurationFromFile() {
        // given
        PrTrackingProps.Repository repoConfig = repoWithFile(".pr-sla.yaml", SLA_48H);
        when(gitHubClient.getFileContent(REPO, ".pr-sla.yaml")).thenReturn("default: 1w");

        // when
        Duration result = slaLookup.getSla(repoConfig, REPO, PR_NUMBER);

        // then
        assertThat(result).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void parsesUppercaseWeekDurationFromFile() {
        // given
        PrTrackingProps.Repository repoConfig = repoWithFile(".pr-sla.yaml", SLA_48H);
        when(gitHubClient.getFileContent(REPO, ".pr-sla.yaml")).thenReturn("default: 2W");

        // when
        Duration result = slaLookup.getSla(repoConfig, REPO, PR_NUMBER);

        // then
        assertThat(result).isEqualTo(Duration.ofDays(14));
    }

    @Test
    void fallsBackToConfigWhenOverrideEntryMissingPath() {
        // given
        PrTrackingProps.Repository repoConfig = repoWithFile(".pr-sla.yaml", SLA_48H);
        when(gitHubClient.getFileContent(REPO, ".pr-sla.yaml"))
                .thenReturn("default: 72h\noverrides:\n  - sla: 24h")
                .thenReturn("default: 72h");

        // when, first call has invalid override (missing path), falls back to config default
        Duration result = slaLookup.getSla(repoConfig, REPO, PR_NUMBER);
        assertThat(result).isEqualTo(SLA_48H);

        // then, invalid file is NOT cached, second call retries and picks up the fix
        Duration retryResult = slaLookup.getSla(repoConfig, REPO, PR_NUMBER);
        assertThat(retryResult).isEqualTo(SLA_72H);
        verify(gitHubClient, times(2)).getFileContent(REPO, ".pr-sla.yaml");
    }

    @Test
    void fallsBackToConfigWhenOverrideEntryMissingSla() {
        // given
        PrTrackingProps.Repository repoConfig = repoWithFile(".pr-sla.yaml", SLA_48H);
        when(gitHubClient.getFileContent(REPO, ".pr-sla.yaml"))
                .thenReturn("default: 72h\noverrides:\n  - path: \"docs/**\"")
                .thenReturn("default: 72h");

        // when, first call has invalid override (missing sla), falls back to config default
        Duration result = slaLookup.getSla(repoConfig, REPO, PR_NUMBER);
        assertThat(result).isEqualTo(SLA_48H);

        // then, invalid file is NOT cached, second call retries and picks up the fix
        Duration retryResult = slaLookup.getSla(repoConfig, REPO, PR_NUMBER);
        assertThat(retryResult).isEqualTo(SLA_72H);
        verify(gitHubClient, times(2)).getFileContent(REPO, ".pr-sla.yaml");
    }

    @Test
    void fileOverridesReplaceConfigOverridesButConfigDefaultPreserved() {
        // given, file has overrides but no default, config has default but no overrides
        PrTrackingProps.Repository repoConfig = repoWithFile(".pr-sla.yaml", SLA_48H);
        when(gitHubClient.getFileContent(REPO, ".pr-sla.yaml"))
                .thenReturn("overrides:\n  - path: \"docs/**\"\n    sla: 24h");
        when(gitHubClient.listPullRequestFiles(REPO, PR_NUMBER)).thenReturn(List.of("docs/README.md"));

        // when
        Duration result = slaLookup.getSla(repoConfig, REPO, PR_NUMBER);

        // then, file override matched, config default (48h) preserved but not used
        assertThat(result).isEqualTo(SLA_24H);
    }

    @Test
    void fileWithOverridesButNoDefaultFallsBackToConfigDefault() {
        // given, file has overrides but no default, PR files don't match any override
        PrTrackingProps.Repository repoConfig = repoWithFile(".pr-sla.yaml", SLA_48H);
        when(gitHubClient.getFileContent(REPO, ".pr-sla.yaml"))
                .thenReturn("overrides:\n  - path: \"docs/**\"\n    sla: 24h");
        when(gitHubClient.listPullRequestFiles(REPO, PR_NUMBER)).thenReturn(List.of("src/Main.java"));

        // when
        Duration result = slaLookup.getSla(repoConfig, REPO, PR_NUMBER);

        // then, no override matched, config default (48h) used
        assertThat(result).isEqualTo(SLA_48H);
    }

    @Test
    void returnsDefaultSlaWhenNoOverridesConfigured() {
        // given
        PrTrackingProps.Repository repoConfig = repo(SLA_48H);

        // when
        Duration result = slaLookup.getSla(repoConfig, REPO, PR_NUMBER);

        // then
        assertThat(result).isEqualTo(SLA_48H);
        verify(gitHubClient, never()).listPullRequestFiles(any(), anyInt());
    }

    private static PrTrackingProps.Repository repo(Duration defaultSla) {
        return new PrTrackingProps.Repository(REPO, "wow", new PrTrackingProps.Sla(null, defaultSla, null));
    }

    private static PrTrackingProps.Repository repoWithOverrides(
            Duration defaultSla, List<PrTrackingProps.SlaOverride> overrides) {
        return new PrTrackingProps.Repository(REPO, "wow", new PrTrackingProps.Sla(null, defaultSla, overrides));
    }

    private static PrTrackingProps.Repository repoWithFile(String file, @Nullable Duration defaultSla) {
        return new PrTrackingProps.Repository(REPO, "wow", new PrTrackingProps.Sla(file, defaultSla, null));
    }
}
