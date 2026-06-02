package com.coreeng.supportbot.prtracking;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.prtracking.source.Provider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GitLabMrUrlParserTest {

    // Keyed on the normalized host[/base-path]/project prefix → canonical repo name, exactly as
    // PrUrlResolver.gitLabRepoPrefixes() builds it.
    private final GitLabMrUrlParser parser = new GitLabMrUrlParser(Map.of(
            "gitlab.com/my-group/project", "my-group/project",
            "gitlab.com/my-group/sub-group/nested", "my-group/sub-group/nested",
            "gitlab.internal.example/infra-team/platform", "infra-team/platform",
            "example.com/gitlab/base-path/service", "base-path/service"));

    @Test
    void detectsTopLevelGroupMr() {
        List<DetectedPr> result = parser.parse("Please review https://gitlab.com/my-group/project/-/merge_requests/42");
        assertThat(result).containsExactly(new DetectedPr(Provider.GITLAB, "my-group/project", 42));
    }

    @Test
    void detectsNestedGroupMr() {
        // Greedy nested-group support is the main reason this parser exists separately from GitHub:
        // `my-group/sub-group/nested` is a valid project path with subgroups.
        List<DetectedPr> result = parser.parse("https://gitlab.com/my-group/sub-group/nested/-/merge_requests/7");
        assertThat(result).containsExactly(new DetectedPr(Provider.GITLAB, "my-group/sub-group/nested", 7));
    }

    @Test
    void detectsSlackFormattedUrl() {
        List<DetectedPr> result = parser.parse("see <https://gitlab.com/my-group/project/-/merge_requests/1|MR title>");
        assertThat(result).containsExactly(new DetectedPr(Provider.GITLAB, "my-group/project", 1));
    }

    @Test
    void detectsSelfHostedHost() {
        List<DetectedPr> result =
                parser.parse("https://gitlab.internal.example/infra-team/platform/-/merge_requests/99");
        assertThat(result).containsExactly(new DetectedPr(Provider.GITLAB, "infra-team/platform", 99));
    }

    @Test
    void detectsSelfHostedInstanceUnderBasePath() {
        // Self-hosted GitLab served under a sub-path (apiBaseUrl https://example.com/gitlab/api/v4):
        // the base path is part of the public MR link and must be matched, not treated as part of
        // the project path.
        List<DetectedPr> result = parser.parse("https://example.com/gitlab/base-path/service/-/merge_requests/12");
        assertThat(result).containsExactly(new DetectedPr(Provider.GITLAB, "base-path/service", 12));
    }

    @Test
    void ignoresUnknownHost() {
        // Belt-and-braces: even if the path matches a tracked repo name, a non-allow-listed host is
        // a strong signal someone copy-pasted a different cluster's URL.
        assertThat(parser.parse("https://gitlab.untrusted.example/my-group/project/-/merge_requests/1"))
                .isEmpty();
    }

    @Test
    void ignoresUntrackedRepository() {
        assertThat(parser.parse("https://gitlab.com/some-other-group/random/-/merge_requests/1"))
                .isEmpty();
    }

    @Test
    void ignoresNonMrPaths() {
        assertThat(parser.parse("https://gitlab.com/my-group/project/-/issues/42"))
                .isEmpty();
        assertThat(parser.parse("https://gitlab.com/my-group/project")).isEmpty();
    }

    @Test
    void deduplicatesSameMrPostedTwice() {
        List<DetectedPr> result = parser.parse("https://gitlab.com/my-group/project/-/merge_requests/7 "
                + "https://gitlab.com/my-group/project/-/merge_requests/7");
        assertThat(result).hasSize(1);
    }

    @Test
    void detectsMultipleDistinctMrs() {
        List<DetectedPr> result = parser.parse("first https://gitlab.com/my-group/project/-/merge_requests/1 "
                + "and https://gitlab.internal.example/infra-team/platform/-/merge_requests/2");
        assertThat(result)
                .hasSize(2)
                .contains(
                        new DetectedPr(Provider.GITLAB, "my-group/project", 1),
                        new DetectedPr(Provider.GITLAB, "infra-team/platform", 2));
    }

    @Test
    void matchesMixedCasePathAgainstCanonicalRepo() {
        // GitLab project paths are case-insensitive for matching; the canonical (lowercased) name is
        // what gets tracked and later used to address the project via the API.
        List<DetectedPr> result = parser.parse("https://gitlab.com/My-Group/Project/-/merge_requests/8");
        assertThat(result).containsExactly(new DetectedPr(Provider.GITLAB, "my-group/project", 8));
    }

    @Test
    void returnsEmptyWhenNoMatch() {
        assertThat(parser.parse("just some text")).isEmpty();
    }

    @Test
    void returnsEmptyWhenNoReposConfigured() {
        GitLabMrUrlParser empty = new GitLabMrUrlParser(Map.of());
        assertThat(empty.parse("https://gitlab.com/my-group/project/-/merge_requests/1"))
                .isEmpty();
    }
}
