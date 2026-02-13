package com.coreeng.supportbot.analysis;

import static com.coreeng.supportbot.dbschema.Tables.ANALYSIS;
import static org.jooq.impl.DSL.row;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JDBC implementation of AnalysisRepository using JOOQ.
 */
@Repository
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JdbcAnalysisRepository implements AnalysisRepository {

    private final DSLContext dsl;

    @Override
    public List<DimensionSummary> getKnowledgeGapCategoriesWithSummaries() {
        String sql = """
            WITH top_categories AS (
                SELECT category, COUNT(*) as query_count
                FROM analysis
                WHERE driver = 'Knowledge Gap'
                GROUP BY category
                ORDER BY COUNT(*) DESC
                LIMIT 5
            ),
            ranked_summaries AS (
                SELECT
                    a.category,
                    tc.query_count,
                    a.summary,
                    ROW_NUMBER() OVER (PARTITION BY a.category ORDER BY a.created_at DESC) as rn
                FROM analysis a
                INNER JOIN top_categories tc ON a.category = tc.category
                WHERE a.driver = 'Knowledge Gap'
            )
            SELECT
                category as dimension,
                query_count,
                summary
            FROM ranked_summaries
            WHERE rn <= 5
            ORDER BY query_count DESC, dimension, rn
            """;

        return dsl.resultQuery(sql)
                .fetch(r -> new DimensionSummary(
                        r.get("dimension", String.class),
                        r.get("query_count", Long.class),
                        r.get("summary", String.class)));
    }

    @Override
    public List<DimensionSummary> getDriversWithSummaries() {
        String sql = """
            WITH top_drivers AS (
                SELECT driver, COUNT(*) as query_count
                FROM analysis
                GROUP BY driver
                ORDER BY COUNT(*) DESC
                LIMIT 5
            ),
            ranked_summaries AS (
                SELECT
                    a.driver,
                    td.query_count,
                    a.summary,
                    ROW_NUMBER() OVER (PARTITION BY a.driver ORDER BY a.created_at DESC) as rn
                FROM analysis a
                INNER JOIN top_drivers td ON a.driver = td.driver
            )
            SELECT
                driver as dimension,
                query_count,
                summary
            FROM ranked_summaries
            WHERE rn <= 5
            ORDER BY query_count DESC, dimension, rn
            """;

        return dsl.resultQuery(sql)
                .fetch(r -> new DimensionSummary(
                        r.get("dimension", String.class),
                        r.get("query_count", Long.class),
                        r.get("summary", String.class)));
    }

    @Override
    @Transactional
    public int deleteAll() {
        return dsl.deleteFrom(ANALYSIS).execute();
    }

    @Override
    @Transactional
    public int batchInsert(List<AnalysisRecord> records) {
        if (records.isEmpty()) {
            return 0;
        }

        return dsl.insertInto(
                        ANALYSIS,
                        ANALYSIS.TICKET_ID,
                        ANALYSIS.DRIVER,
                        ANALYSIS.CATEGORY,
                        ANALYSIS.FEATURE,
                        ANALYSIS.SUMMARY)
                .valuesOfRows(records.stream()
                        .map(r -> row(r.ticketId(), r.driver(), r.category(), r.feature(), r.summary()))
                        .toList())
                .execute();
    }
}
