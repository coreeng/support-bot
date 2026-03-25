package com.coreeng.supportbot.prtracking;

import static com.coreeng.supportbot.dbschema.Tables.PR_TRACKING;
import static com.google.common.base.Preconditions.checkNotNull;

import com.coreeng.supportbot.dbschema.enums.PrTrackingStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
@Transactional
public class JdbcPrTrackingRepository implements PrTrackingRepository {

    private final DSLContext dsl;

    @Override
    public @Nullable PrTrackingRecord insertIfAbsent(NewPrTracking newRecord) {
        com.coreeng.supportbot.dbschema.tables.records.PrTrackingRecord row = dsl.insertInto(PR_TRACKING)
                .set(PR_TRACKING.TICKET_ID, newRecord.ticketId())
                .set(PR_TRACKING.GITHUB_REPO, newRecord.githubRepo())
                .set(PR_TRACKING.PR_NUMBER, newRecord.prNumber())
                .set(PR_TRACKING.PR_CREATED_AT, newRecord.prCreatedAt())
                .set(PR_TRACKING.SLA_DEADLINE, newRecord.slaDeadline())
                .set(PR_TRACKING.OWNING_TEAM, newRecord.owningTeam())
                .set(PR_TRACKING.CAN_AUTO_CLOSE_TICKET, newRecord.canAutoCloseTicket())
                .onConflict(PR_TRACKING.TICKET_ID, PR_TRACKING.GITHUB_REPO, PR_TRACKING.PR_NUMBER)
                .doNothing()
                .returning()
                .fetchOne();
        return row == null ? null : toRecord(row);
    }

    @Transactional(readOnly = true)
    @Override
    public List<PrTrackingRecord> findAllByStatus(PrTrackingStatus status) {
        return dsl.selectFrom(PR_TRACKING)
                .where(PR_TRACKING.STATUS.eq(status))
                .fetch()
                .map(JdbcPrTrackingRepository::toRecord);
    }

    @Transactional(readOnly = true)
    @Override
    public List<PrTrackingRecord> findAllActive() {
        return dsl.selectFrom(PR_TRACKING)
                .where(PR_TRACKING.STATUS.in(PrTrackingStatus.OPEN, PrTrackingStatus.ESCALATED))
                .fetch()
                .map(JdbcPrTrackingRepository::toRecord);
    }

    @Override
    public PrTrackingRecord updateStatus(
            long id, PrTrackingStatus newStatus, @Nullable Instant closedAt, @Nullable Long escalationId) {
        com.coreeng.supportbot.dbschema.tables.records.PrTrackingRecord row = dsl.update(PR_TRACKING)
                .set(PR_TRACKING.STATUS, newStatus)
                .set(PR_TRACKING.CLOSED_AT, closedAt)
                .set(PR_TRACKING.ESCALATION_ID, escalationId)
                .where(PR_TRACKING.ID.eq(id))
                .returning()
                .fetchOptional()
                .orElseThrow(() -> new IllegalStateException("PR tracking record not found for id " + id));
        return toRecord(row);
    }

    @Transactional(readOnly = true)
    @Override
    public boolean hasAnyActiveForTicket(long ticketId) {
        return dsl.fetchExists(
                PR_TRACKING,
                PR_TRACKING
                        .TICKET_ID
                        .eq(ticketId)
                        .and(PR_TRACKING.STATUS.in(PrTrackingStatus.OPEN, PrTrackingStatus.ESCALATED)));
    }

    @Transactional(readOnly = true)
    @Override
    public boolean hasAnyActiveClosableForTicket(long ticketId) {
        return dsl.fetchExists(
                PR_TRACKING,
                PR_TRACKING
                        .TICKET_ID
                        .eq(ticketId)
                        .and(PR_TRACKING.CAN_AUTO_CLOSE_TICKET.isTrue())
                        .and(PR_TRACKING.STATUS.in(PrTrackingStatus.OPEN, PrTrackingStatus.ESCALATED)));
    }

    @Transactional(readOnly = true)
    @Override
    public boolean existsByTicketIdAndRepoAndPrNumber(long ticketId, String githubRepo, int prNumber) {
        return dsl.fetchExists(
                PR_TRACKING,
                PR_TRACKING
                        .TICKET_ID
                        .eq(ticketId)
                        .and(PR_TRACKING.GITHUB_REPO.eq(githubRepo))
                        .and(PR_TRACKING.PR_NUMBER.eq(prNumber)));
    }

    @Transactional(readOnly = true)
    @Override
    public List<RepoInsights> getInsightsByRepo(@Nullable LocalDate dateFrom, @Nullable LocalDate dateTo) {
        String dateFilter = buildDateFilter(dateFrom, dateTo, "pr_created_at");
        String sql = """
                SELECT
                    github_repo,
                    MIN(owning_team) AS owning_team,
                    COUNT(*) AS pr_count,
                    COUNT(*) FILTER (WHERE status = 'OPEN' OR status = 'ESCALATED') AS open_count,
                    COUNT(*) FILTER (WHERE escalation_id IS NOT NULL) AS escalated_count,
                    COUNT(*) FILTER (WHERE sla_deadline < COALESCE(closed_at, now())) AS breached_count,
                    percentile_cont(0.5) WITHIN GROUP (ORDER BY lifetime) AS p50,
                    percentile_cont(0.9) WITHIN GROUP (ORDER BY lifetime) AS p90,
                    percentile_cont(0.99) WITHIN GROUP (ORDER BY lifetime) AS p99
                FROM (
                    SELECT github_repo, owning_team, status, escalation_id, sla_deadline, closed_at,
                        EXTRACT(EPOCH FROM
                            CASE WHEN closed_at IS NOT NULL THEN closed_at - pr_created_at
                                 ELSE now() - pr_created_at END
                        ) AS lifetime
                    FROM pr_tracking
                    WHERE 1=1
                      %s
                ) sub
                GROUP BY github_repo
                ORDER BY github_repo
                """.formatted(dateFilter);

        return dsl.resultQuery(sql)
                .fetch(r -> new RepoInsights(
                        r.get("github_repo", String.class),
                        r.get("owning_team", String.class),
                        nullToZero(r.get("pr_count", Long.class)),
                        nullToZero(r.get("open_count", Long.class)),
                        nullToZero(r.get("escalated_count", Long.class)),
                        nullToZero(r.get("breached_count", Long.class)),
                        nullToZero(r.get("p50", Double.class)),
                        nullToZero(r.get("p90", Double.class)),
                        nullToZero(r.get("p99", Double.class))));
    }

    private static String buildDateFilter(@Nullable LocalDate dateFrom, @Nullable LocalDate dateTo, String column) {
        StringBuilder sb = new StringBuilder();
        if (dateFrom != null) {
            sb.append("AND ")
                    .append(column)
                    .append("::date >= '")
                    .append(dateFrom.format(DateTimeFormatter.ISO_LOCAL_DATE))
                    .append("'::date ");
        }
        if (dateTo != null) {
            sb.append("AND ")
                    .append(column)
                    .append("::date <= '")
                    .append(dateTo.format(DateTimeFormatter.ISO_LOCAL_DATE))
                    .append("'::date ");
        }
        return sb.toString();
    }

    private static double nullToZero(Double value) {
        return Objects.requireNonNullElse(value, 0.0);
    }

    private static long nullToZero(Long value) {
        return Objects.requireNonNullElse(value, 0L);
    }

    private static PrTrackingRecord toRecord(com.coreeng.supportbot.dbschema.tables.records.PrTrackingRecord row) {
        return new PrTrackingRecord(
                checkNotNull(row.getId()),
                checkNotNull(row.getTicketId()),
                checkNotNull(row.getGithubRepo()),
                checkNotNull(row.getPrNumber()),
                checkNotNull(row.getPrCreatedAt()),
                checkNotNull(row.getSlaDeadline()),
                checkNotNull(row.getOwningTeam()),
                checkNotNull(row.getCanAutoCloseTicket()),
                checkNotNull(row.getStatus()),
                row.getEscalationId(),
                row.getClosedAt());
    }
}
