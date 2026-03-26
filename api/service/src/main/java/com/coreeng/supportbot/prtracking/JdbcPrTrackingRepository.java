package com.coreeng.supportbot.prtracking;

import static com.coreeng.supportbot.dbschema.Tables.PR_TRACKING;
import static com.coreeng.supportbot.util.JooqUtils.nullToZero;
import static com.google.common.base.Preconditions.checkNotNull;

import com.coreeng.supportbot.dbschema.enums.PrTrackingStatus;
import com.coreeng.supportbot.escalation.EscalationSource;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.types.YearToSecond;
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
                .where(PR_TRACKING.STATUS.in(
                        PrTrackingStatus.OPEN,
                        PrTrackingStatus.ESCALATED,
                        PrTrackingStatus.CHANGES_REQUESTED,
                        PrTrackingStatus.APPROVED))
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
                        .and(PR_TRACKING.STATUS.in(
                                PrTrackingStatus.OPEN,
                                PrTrackingStatus.ESCALATED,
                                PrTrackingStatus.CHANGES_REQUESTED,
                                PrTrackingStatus.APPROVED)));
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
                        .and(PR_TRACKING.STATUS.in(
                                PrTrackingStatus.OPEN,
                                PrTrackingStatus.ESCALATED,
                                PrTrackingStatus.CHANGES_REQUESTED,
                                PrTrackingStatus.APPROVED)));
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
        List<Object> binds = new ArrayList<>();
        binds.add(EscalationSource.bot.name());
        binds.add(EscalationSource.manual.name());
        String dateFilter = buildDateFilter(dateFrom, dateTo, "pr_created_at", binds);
        String sql = """
                SELECT
                    github_repo,
                    COALESCE(MIN(owning_team), 'unknown') AS owning_team,
                    COUNT(*) AS pr_count,
                    COUNT(*) FILTER (WHERE status IN ('OPEN', 'ESCALATED', 'CHANGES_REQUESTED', 'APPROVED')) AS open_count,
                    COUNT(*) FILTER (WHERE status = 'ESCALATED') AS escalated_count,
                    COUNT(*) FILTER (WHERE sla_deadline < COALESCE(closed_at, now())) AS breached_count,
                    COUNT(DISTINCT ticket_id) FILTER (
                        WHERE EXISTS (
                            SELECT 1 FROM escalation e
                            WHERE e.ticket_id = sub.ticket_id AND e.source = ?
                        )
                    ) AS bot_escalated_count,
                    COUNT(DISTINCT ticket_id) FILTER (
                        WHERE EXISTS (
                            SELECT 1 FROM escalation e
                            WHERE e.ticket_id = sub.ticket_id AND e.source = ?
                        )
                    ) AS manual_escalated_count,
                    percentile_cont(0.5) WITHIN GROUP (ORDER BY lifetime) AS p50,
                    percentile_cont(0.9) WITHIN GROUP (ORDER BY lifetime) AS p90,
                    percentile_cont(0.99) WITHIN GROUP (ORDER BY lifetime) AS p99
                FROM (
                    SELECT github_repo, owning_team, status, sla_deadline, closed_at, ticket_id,
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

        return dsl.resultQuery(sql, binds.toArray())
                .fetch(r -> new RepoInsights(
                        r.get("github_repo", String.class),
                        r.get("owning_team", String.class),
                        r.get("pr_count", Long.class),
                        r.get("open_count", Long.class),
                        r.get("escalated_count", Long.class),
                        r.get("breached_count", Long.class),
                        nullToZero(r.get("bot_escalated_count", Long.class)),
                        nullToZero(r.get("manual_escalated_count", Long.class)),
                        nullToZero(r.get("p50", Double.class)),
                        nullToZero(r.get("p90", Double.class)),
                        nullToZero(r.get("p99", Double.class))));
    }

    @Transactional(readOnly = true)
    @Override
    public EscalationBreakdown getEscalationBreakdown(@Nullable LocalDate dateFrom, @Nullable LocalDate dateTo) {
        List<Object> binds = new ArrayList<>();
        binds.add(EscalationSource.bot.name());
        binds.add(EscalationSource.manual.name());
        String dateFilter = buildDateFilter(dateFrom, dateTo, "pr_created_at", binds);
        String sql = """
                SELECT
                    COUNT(DISTINCT ticket_id) AS total_pr_tickets,
                    COUNT(DISTINCT ticket_id) FILTER (
                        WHERE EXISTS (
                            SELECT 1 FROM escalation e
                            WHERE e.ticket_id = pt.ticket_id AND e.source = ?
                        )
                    ) AS bot_escalated_tickets,
                    COUNT(DISTINCT ticket_id) FILTER (
                        WHERE EXISTS (
                            SELECT 1 FROM escalation e
                            WHERE e.ticket_id = pt.ticket_id AND e.source = ?
                        )
                    ) AS manually_escalated_tickets
                FROM pr_tracking pt
                WHERE 1=1
                  %s
                """.formatted(dateFilter);

        return checkNotNull(dsl.resultQuery(sql, binds.toArray())
                .fetchOne(r -> new EscalationBreakdown(
                        r.get("total_pr_tickets", Long.class),
                        r.get("bot_escalated_tickets", Long.class),
                        r.get("manually_escalated_tickets", Long.class))));
    }

    private static String buildDateFilter(
            @Nullable LocalDate dateFrom, @Nullable LocalDate dateTo, String column, List<Object> binds) {
        StringBuilder sb = new StringBuilder();
        if (dateFrom != null) {
            sb.append("AND ").append(column).append("::date >= ?::date ");
            binds.add(dateFrom);
        }
        if (dateTo != null) {
            sb.append("AND ").append(column).append("::date <= ?::date ");
            binds.add(dateTo);
        }
        return sb.toString();
    }

    private static PrTrackingRecord toRecord(com.coreeng.supportbot.dbschema.tables.records.PrTrackingRecord row) {
        YearToSecond slaRemainingRaw = row.getSlaRemaining();
        Duration slaRemaining = slaRemainingRaw != null ? slaRemainingRaw.toDuration() : null;
        return new PrTrackingRecord(
                checkNotNull(row.getId()),
                checkNotNull(row.getTicketId()),
                checkNotNull(row.getGithubRepo()),
                checkNotNull(row.getPrNumber()),
                checkNotNull(row.getPrCreatedAt()),
                row.getSlaDeadline(),
                checkNotNull(row.getOwningTeam()),
                checkNotNull(row.getCanAutoCloseTicket()),
                checkNotNull(row.getStatus()),
                row.getEscalationId(),
                row.getClosedAt(),
                slaRemaining,
                row.getLastReviewAt(),
                row.getLastAuthorActivityAt());
    }
}
