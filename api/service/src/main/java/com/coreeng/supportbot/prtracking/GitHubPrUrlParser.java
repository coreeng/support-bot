package com.coreeng.supportbot.prtracking;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitHubPrUrlParser {
    private static final Logger log = LoggerFactory.getLogger(GitHubPrUrlParser.class);

    // Matches plain and Slack-formatted URLs: https://github.com/org/repo/pull/123
    // Slack wraps links as <https://...> or <https://...|display text>, but the regex
    // matches the URL portion regardless of surrounding characters.
    private static final Pattern PR_URL_PATTERN =
            Pattern.compile("https?://github\\.com/([\\w.-]+/[\\w.-]+)/pull/(\\d+)");

    private final Set<String> trackedRepositories;

    public GitHubPrUrlParser(Set<String> trackedRepositories) {
        this.trackedRepositories = Set.copyOf(trackedRepositories);
    }

    /**
     * Extracts all in-scope PR references from a Slack message text.
     * Unrecognised repositories are silently ignored.
     * Duplicate links to the same PR within a single message are deduplicated.
     */
    public List<DetectedPr> parse(String messageText) {
        List<DetectedPr> results = new ArrayList<>();
        Matcher matcher = PR_URL_PATTERN.matcher(messageText);
        while (matcher.find()) {
            String repoName = matcher.group(1).toLowerCase(Locale.ROOT);
            int pullNumber;
            try {
                pullNumber = Integer.parseInt(matcher.group(2));
            } catch (NumberFormatException e) {
                // Skip malformed / overflow PR numbers and continue parsing others.
                log.atWarn()
                        .addArgument(() -> matcher.group(0))
                        .addArgument(() -> matcher.group(2))
                        .log("Skipping PR URL due to invalid PR number: url={}, pullNumber={}");
                continue;
            }
            DetectedPr candidate = new DetectedPr(repoName, pullNumber);
            if (trackedRepositories.contains(repoName) && !results.contains(candidate)) {
                results.add(candidate);
            }
        }
        return List.copyOf(results);
    }
}
