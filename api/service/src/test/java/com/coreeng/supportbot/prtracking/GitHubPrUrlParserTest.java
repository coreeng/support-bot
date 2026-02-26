package com.coreeng.supportbot.prtracking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GitHubPrUrlParserTest {

    private final GitHubPrUrlParser parser =
            new GitHubPrUrlParser(Set.of("my-org/onboarding-repo", "my-org/another-repo"));

    @Test
    void detectsPlainUrl() {
        List<DetectedPr> result =
                parser.parse("Please review https://github.com/my-org/onboarding-repo/pull/42");

        assertThat(result).containsExactly(new DetectedPr("my-org/onboarding-repo", 42));
    }

    @Test
    void detectsSlackFormattedUrl() {
        List<DetectedPr> result =
                parser.parse("Please review <https://github.com/my-org/onboarding-repo/pull/42>");

        assertThat(result).containsExactly(new DetectedPr("my-org/onboarding-repo", 42));
    }

    @Test
    void detectsSlackFormattedUrlWithDisplayText() {
        List<DetectedPr> result = parser.parse(
                "Please review <https://github.com/my-org/onboarding-repo/pull/42|my PR title>");

        assertThat(result).containsExactly(new DetectedPr("my-org/onboarding-repo", 42));
    }

    @Test
    void detectsMultiplePrsInOneMessage() {
        List<DetectedPr> result = parser.parse(
                "Two PRs: https://github.com/my-org/onboarding-repo/pull/1 "
                        + "and https://github.com/my-org/another-repo/pull/99");

        assertThat(result)
                .containsExactlyInAnyOrder(
                        new DetectedPr("my-org/onboarding-repo", 1),
                        new DetectedPr("my-org/another-repo", 99));
    }

    @Test
    void deduplicatesSamePrLinkPostedTwice() {
        List<DetectedPr> result = parser.parse(
                "https://github.com/my-org/onboarding-repo/pull/7 "
                        + "https://github.com/my-org/onboarding-repo/pull/7");

        assertThat(result).containsExactly(new DetectedPr("my-org/onboarding-repo", 7));
    }

    @Test
    void ignoresUnknownRepository() {
        List<DetectedPr> result =
                parser.parse("https://github.com/some-other-org/untracked-repo/pull/5");

        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyWhenNoLinksPresent() {
        assertThat(parser.parse("Just a plain message with no links")).isEmpty();
    }

    @Test
    void detectsHttpUrl() {
        List<DetectedPr> result =
                parser.parse("http://github.com/my-org/onboarding-repo/pull/10");

        assertThat(result).containsExactly(new DetectedPr("my-org/onboarding-repo", 10));
    }

    @Test
    void ignoresNonPrGitHubUrls() {
        assertThat(parser.parse("https://github.com/my-org/onboarding-repo/issues/42")).isEmpty();
        assertThat(parser.parse("https://github.com/my-org/onboarding-repo")).isEmpty();
    }
}
