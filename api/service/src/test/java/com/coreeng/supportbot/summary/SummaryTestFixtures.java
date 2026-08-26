package com.coreeng.supportbot.summary;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Shared query/ticket/analysis fixtures for the Postgres-backed tests of the Support Summary read
 * side and the windowed gap query.
 *
 * <p>These tests run against the same local database as {@code make run-local}, so every fixture is
 * scoped to synthetic channel IDs and {@link #clear} removes only those rows — real local data is
 * left alone.
 */
public final class SummaryTestFixtures {

    private SummaryTestFixtures() {}

    /**
     * Inserts a query plus its ticket.
     *
     * @param raisedAt the ticket-creation wall-clock time in the JVM default zone, stored on
     *     {@code query.date}. Fixtures are expressed as local time because that is the zone the
     *     JDBC session runs in, and the window bounds are {@code LocalDate}s cast in that same zone —
     *     an instant-based fixture would land on the wrong side of a boundary outside UTC.
     * @return the generated ticket id
     */
    public static long insertTicket(
            JdbcTemplate jdbcTemplate,
            String channelId,
            String ts,
            LocalDateTime raisedAt,
            String status,
            @Nullable String team) {
        Long queryId = jdbcTemplate.queryForObject(
                "INSERT INTO query (ts, channel_id, date) VALUES (?, ?, ?) RETURNING id",
                Long.class,
                ts,
                channelId,
                Timestamp.valueOf(raisedAt));
        Long ticketId = jdbcTemplate.queryForObject(
                "INSERT INTO ticket (query_id, status, team) VALUES (?, ?::ticket_status, ?) RETURNING id",
                Long.class,
                queryId,
                status,
                team);
        if (ticketId == null) {
            throw new IllegalStateException("ticket insert returned no id");
        }
        return ticketId;
    }

    public static void insertAnalysis(
            JdbcTemplate jdbcTemplate,
            long ticketId,
            String driver,
            String category,
            String feature,
            String summary,
            String promptId) {
        insertAnalysis(jdbcTemplate, ticketId, driver, category, feature, summary, promptId, null);
    }

    /** Same, but pins {@code updated_at} — the summary cache fingerprint is derived from it. */
    public static void insertAnalysis(
            JdbcTemplate jdbcTemplate,
            long ticketId,
            String driver,
            String category,
            String feature,
            String summary,
            String promptId,
            @Nullable LocalDateTime updatedAt) {
        jdbcTemplate.update("""
                INSERT INTO analysis (ticket_id, driver, category, feature, summary, prompt_id, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, coalesce(?, CURRENT_TIMESTAMP))
                """, Math.toIntExact(ticketId), driver, category, feature, summary, promptId, updatedAt);
    }

    /** Removes every fixture row belonging to the given synthetic channels. */
    public static void clear(JdbcTemplate jdbcTemplate, String... channelIds) {
        for (String channelId : channelIds) {
            jdbcTemplate.update("""
                    DELETE FROM analysis
                     WHERE ticket_id IN (
                        SELECT t.id FROM ticket t JOIN query q ON q.id = t.query_id WHERE q.channel_id = ?
                     )
                    """, channelId);
            jdbcTemplate.update(
                    "DELETE FROM ticket WHERE query_id IN (SELECT id FROM query WHERE channel_id = ?)", channelId);
            jdbcTemplate.update("DELETE FROM query WHERE channel_id = ?", channelId);
        }
    }
}
