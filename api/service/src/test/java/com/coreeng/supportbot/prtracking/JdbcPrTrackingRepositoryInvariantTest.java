package com.coreeng.supportbot.prtracking;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Source-level guardrails against silent regressions in {@link JdbcPrTrackingRepository}. These
 * tests don't spin up the DB (all DB-bound tests live in the functional harness) — they enforce
 * one-line invariants that would be easy to accidentally break during refactors and whose
 * consequences don't surface until production dashboards start misclassifying repos.
 */
class JdbcPrTrackingRepositoryInvariantTest {

    private static final Path SOURCE =
            Path.of("src/main/java/com/coreeng/supportbot/prtracking/JdbcPrTrackingRepository.java");

    /**
     * has_sla is set once on insert and must never be modified afterwards. A stray {@code
     * setNull(HAS_SLA)} or {@code set(HAS_SLA, ...)} in updateStatus, pauseSla, or resumeSla would
     * silently break the insights query's {@code BOOL_OR(has_sla)} aggregate — a repo whose PRs
     * all close (or pause, or resume) would flip its has_sla bit to false and the Breached column
     * in the dashboard would disappear with no error. We can't catch this with Mockito-level
     * verification because the jOOQ update builder is fluent on the DSLContext; a source-level
     * scan is the most reliable tripwire.
     */
    @Test
    void hasSlaIsNeverMutatedAfterInsert() throws IOException {
        String source = Files.readString(SOURCE);

        // Strip the insertIfAbsent body — that's the legitimate one-and-only site that sets HAS_SLA.
        String afterInsert = removeMethod(source, "insertIfAbsent");

        assertThat(afterInsert)
                .as("HAS_SLA must only be set in insertIfAbsent; any other reference would flip the "
                        + "insights BOOL_OR(has_sla) aggregate silently. If you genuinely need to "
                        + "mutate has_sla post-insert, update this test and document why in the "
                        + "V15 migration file.")
                .doesNotContain("HAS_SLA");
    }

    /**
     * escalated_count in getInsightsByRepo must count MERGE_ESCALATED alongside ESCALATED. The two are
     * escalation-equivalent everywhere else — the query's own severity CASEs, the UI severity map, the red
     * badge — so filtering on ESCALATED alone silently under-reports merge-escalated repos as healthy in
     * the stat card and the severity sort, with no error. The DB-bound tests live in the functional harness;
     * this one-token filter is easy to regress in a refactor, hence a source-level tripwire.
     */
    @Test
    void escalatedCountIncludesMergeEscalated() throws IOException {
        String source = Files.readString(SOURCE);
        assertThat(source)
                .as("escalated_count must count both ESCALATED and MERGE_ESCALATED, not ESCALATED alone")
                .contains("status IN ('ESCALATED', 'MERGE_ESCALATED')) AS escalated_count")
                .doesNotContain("status = 'ESCALATED') AS escalated_count");
    }

    /**
     * Extracts-and-strips the body of the named method. We use a brace-counting scan rather than a
     * regex because the body contains nested braces (lambda bodies, inner records, etc.).
     */
    private static String removeMethod(String source, String methodName) {
        // Match a method declaration line containing the name followed by `(`.
        Pattern start = Pattern.compile("\\b" + Pattern.quote(methodName) + "\\s*\\(");
        Matcher m = start.matcher(source);
        if (!m.find()) {
            throw new AssertionError("Method " + methodName + " not found in " + SOURCE);
        }
        int openBrace = source.indexOf('{', m.end());
        if (openBrace < 0) {
            throw new AssertionError("Opening brace for " + methodName + " not found");
        }
        int depth = 1;
        int i = openBrace + 1;
        while (i < source.length() && depth > 0) {
            char c = source.charAt(i++);
            if (c == '{') depth++;
            else if (c == '}') depth--;
        }
        return source.substring(0, m.start()) + source.substring(i);
    }
}
