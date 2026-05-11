package com.coreeng.supportbot.prtracking;

import com.coreeng.supportbot.prtracking.source.Provider;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects GitLab MR URLs in Slack message text. URL shape is
 * {@code https://<host>/<group>/<subgroup?>/.../<project>/-/merge_requests/<iid>} — nested
 * groups are valid, so the project path is matched lazily up to the {@code /-/} separator.
 *
 * <p>Two filters keep matches safe: (1) host must be in the configured GitLab host allow-list
 * (which includes gitlab.com and any self-hosted apiBaseUrl host the operator declared), and
 * (2) the resulting project path must be in the tracked-repos set. Either filter alone would
 * leak some false positives — both together mean only intended links are tracked.
 */
public class GitLabMrUrlParser {

    private static final Logger LOG = LoggerFactory.getLogger(GitLabMrUrlParser.class);

    // host: scheme + first path segment.
    // project path: lazy match across nested groups, stopping at the literal /-/ separator.
    private static final Pattern MR_URL_PATTERN =
            Pattern.compile("https?://([^/\\s|<>]+)/([^\\s|<>]+?)/-/merge_requests/(\\d+)");

    private final Set<String> allowedHosts;
    private final Set<String> trackedRepositories;

    public GitLabMrUrlParser(Set<String> allowedHosts, Set<String> trackedRepositories) {
        this.allowedHosts = Set.copyOf(allowedHosts);
        this.trackedRepositories = Set.copyOf(trackedRepositories);
    }

    /**
     * Extracts all in-scope MR references from a Slack message text. Unrecognised hosts or
     * untracked project paths are silently ignored. Duplicates within a single message are
     * deduplicated.
     */
    public List<DetectedPr> parse(String messageText) {
        if (allowedHosts.isEmpty() || trackedRepositories.isEmpty()) {
            return List.of();
        }
        List<DetectedPr> results = new ArrayList<>();
        Matcher matcher = MR_URL_PATTERN.matcher(messageText);
        while (matcher.find()) {
            String host = matcher.group(1).toLowerCase(Locale.ROOT);
            if (!allowedHosts.contains(host)) {
                continue;
            }
            String projectPath = matcher.group(2).toLowerCase(Locale.ROOT);
            int iid;
            try {
                iid = Integer.parseInt(matcher.group(3));
            } catch (NumberFormatException e) {
                LOG.atWarn()
                        .addArgument(() -> matcher.group(0))
                        .addArgument(() -> matcher.group(3))
                        .log("Skipping MR URL due to invalid IID: url={}, iid={}");
                continue;
            }
            if (!trackedRepositories.contains(projectPath)) {
                continue;
            }
            DetectedPr candidate = new DetectedPr(Provider.GITLAB, projectPath, iid);
            if (!results.contains(candidate)) {
                results.add(candidate);
            }
        }
        return List.copyOf(results);
    }
}
