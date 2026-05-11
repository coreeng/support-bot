package com.coreeng.supportbot.prtracking;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.prtracking.source.Provider;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GitLabMrUrlParserTest {

    private final GitLabMrUrlParser parser = new GitLabMrUrlParser(
            Set.of("gitlab.com", "gitlab.internal.example"),
            Set.of("my-group/project", "my-group/sub-group/nested", "infra-team/platform"));

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
    void normalizesMixedCasePath() {
        List<DetectedPr> result = parser.parse("https://gitlab.com/My-Group/Project/-/merge_requests/8");
        assertThat(result).containsExactly(new DetectedPr(Provider.GITLAB, "my-group/project", 8));
    }

    @Test
    void returnsEmptyWhenNoMatch() {
        assertThat(parser.parse("just some text")).isEmpty();
    }

    @Test
    void returnsEmptyWhenNoAllowedHostsConfigured() {
        GitLabMrUrlParser empty = new GitLabMrUrlParser(Set.of(), Set.of("my-group/project"));
        assertThat(empty.parse("https://gitlab.com/my-group/project/-/merge_requests/1"))
                .isEmpty();
    }
}
