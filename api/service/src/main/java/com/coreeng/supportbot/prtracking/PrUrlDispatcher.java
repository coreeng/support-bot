package com.coreeng.supportbot.prtracking;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Runs both provider URL parsers against a single message and returns the combined, de-duplicated
 * result. Callers downstream (PrDetectionService, message containment check) treat the list
 * provider-by-provider — order matters only for tests and for the order notifications are posted.
 */
public class PrUrlDispatcher {

    private final GitHubPrUrlParser gitHubParser;
    private final GitLabMrUrlParser gitLabParser;

    public PrUrlDispatcher(GitHubPrUrlParser gitHubParser, GitLabMrUrlParser gitLabParser) {
        this.gitHubParser = gitHubParser;
        this.gitLabParser = gitLabParser;
    }

    public List<DetectedPr> parse(String messageText) {
        // LinkedHashSet preserves order while deduping in case a future regex change accidentally
        // makes the two parsers overlap (e.g. a Slack-formatted GitHub URL someone embeds in a
        // GitLab MR description). Today the two host sets are disjoint so the set is just safety.
        Set<DetectedPr> deduped = new LinkedHashSet<>();
        deduped.addAll(gitHubParser.parse(messageText));
        deduped.addAll(gitLabParser.parse(messageText));
        return List.copyOf(new ArrayList<>(deduped));
    }
}
