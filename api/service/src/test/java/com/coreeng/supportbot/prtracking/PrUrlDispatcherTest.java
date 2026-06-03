package com.coreeng.supportbot.prtracking;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.prtracking.source.Provider;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PrUrlDispatcherTest {

    private final PrUrlDispatcher dispatcher = new PrUrlDispatcher(
            new GitHubPrUrlParser(Set.of("my-org/my-repo")),
            new GitLabMrUrlParser(Map.of("gitlab.com/my-group/project", "my-group/project")));

    @Test
    void fansOutToBothParsers() {
        List<DetectedPr> result = dispatcher.parse("Two refs: https://github.com/my-org/my-repo/pull/1 "
                + "and https://gitlab.com/my-group/project/-/merge_requests/2");

        assertThat(result)
                .hasSize(2)
                .contains(
                        new DetectedPr(Provider.GITHUB, "my-org/my-repo", 1),
                        new DetectedPr(Provider.GITLAB, "my-group/project", 2));
    }

    @Test
    void returnsEmptyWhenNoMatch() {
        assertThat(dispatcher.parse("nothing here")).isEmpty();
    }

    @Test
    void dedupesAcrossParsers() {
        // Defensive: even though today's regexes can't overlap on the same URL, the dispatcher
        // should not yield duplicate entries if they ever do.
        List<DetectedPr> result =
                dispatcher.parse("https://github.com/my-org/my-repo/pull/1 https://github.com/my-org/my-repo/pull/1");
        assertThat(result).hasSize(1);
    }
}
