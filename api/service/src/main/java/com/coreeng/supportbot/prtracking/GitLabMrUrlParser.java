package com.coreeng.supportbot.prtracking;

import com.coreeng.supportbot.prtracking.source.Provider;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects GitLab MR URLs in Slack message text. URL shape is
 * {@code https://<host>[/<base-path>]/<group>/<subgroup?>/.../<project>/-/merge_requests/<iid>} —
 * nested groups are valid, so the project path is matched lazily up to the {@code /-/} separator.
 *
 * <p>Matching is keyed on each tracked repo's full normalized link prefix
 * ({@code host[/base-path]/project}, lowercased — see {@link PrUrlResolver#gitLabRepoPrefixes()})
 * rather than host and project path separately. Matching the whole prefix is what lets self-hosted
 * instances served under a base path resolve, ties each link to exactly one configured repo (so a
 * foreign cluster's URL can't slip through on a matching project name alone), and stays
 * case-insensitive on the project path.
 */
public class GitLabMrUrlParser {

    private static final Logger LOG = LoggerFactory.getLogger(GitLabMrUrlParser.class);

    // host: scheme + first path segment.
    // project path (incl. any base path): lazy match across nested groups, stopping at the /-/ separator.
    private static final Pattern MR_URL_PATTERN =
            Pattern.compile("https?://([^/\\s|<>]+)/([^\\s|<>]+?)/-/merge_requests/(\\d+)");

    /** Normalized {@code host[/base-path]/project} prefix → canonical (lowercased) repo name. */
    private final Map<String, String> repoByUrlPrefix;

    public GitLabMrUrlParser(Map<String, String> repoByUrlPrefix) {
        this.repoByUrlPrefix = Map.copyOf(repoByUrlPrefix);
    }

    /**
     * Extracts all in-scope MR references from a Slack message text. Links whose
     * {@code host[/base-path]/project} prefix doesn't match a tracked repo are silently ignored.
     * Duplicates within a single message are deduplicated.
     */
    public List<DetectedPr> parse(String messageText) {
        if (repoByUrlPrefix.isEmpty()) {
            return List.of();
        }
        List<DetectedPr> results = new ArrayList<>();
        Matcher matcher = MR_URL_PATTERN.matcher(messageText);
        while (matcher.find()) {
            String prefix = (matcher.group(1) + "/" + matcher.group(2)).toLowerCase(Locale.ROOT);
            String repoName = repoByUrlPrefix.get(prefix);
            if (repoName == null) {
                continue;
            }
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
            DetectedPr candidate = new DetectedPr(Provider.GITLAB, repoName, iid);
            if (!results.contains(candidate)) {
                results.add(candidate);
            }
        }
        return List.copyOf(results);
    }
}
