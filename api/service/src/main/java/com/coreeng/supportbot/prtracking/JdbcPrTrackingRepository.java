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
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.types.DayToSecond;
import org.jooq.types.YearToMonth;
import org.jooq.types.YearToSecond;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
@Transactional
@Slf4j
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
    public @Nullable PrTrackingRecord findById(long id) {
        com.coreeng.supportbot.dbschema.tables.records.PrTrackingRecord row =
                dsl.selectFrom(PR_TRACKING).where(PR_TRACKING.ID.eq(id)).fetchOne();
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
        var query = dsl.update(PR_TRACKING)
                .set(PR_TRACKING.STATUS, newStatus)
                .set(PR_TRACKING.CLOSED_AT, closedAt)
                .set(PR_TRACKING.ESCALATION_ID, escalationId);
        if (newStatus == PrTrackingStatus.CLOSED) {
            query = query.setNull(PR_TRACKING.SLA_DEADLINE).setNull(PR_TRACKING.SLA_REMAINING);
        }
        com.coreeng.supportbot.dbschema.tables.records.PrTrackingRecord row = query.where(PR_TRACKING.ID.eq(id))
                .returning()
                .fetchOptional()
                .orElseThrow(() -> new IllegalStateException("PR tracking record not found for id " + id));
        return toRecord(row);
    }

    @Override
    public PrTrackingRecord pauseSla(long id, PrTrackingStatus newStatus, Duration remaining) {
        if (newStatus != PrTrackingStatus.CHANGES_REQUESTED && newStatus != PrTrackingStatus.APPROVED) {
            throw new IllegalArgumentException(
                    "pauseSla only supports CHANGES_REQUESTED or APPROVED, got: " + newStatus);
        }
        com.coreeng.supportbot.dbschema.tables.records.PrTrackingRecord row = dsl.update(PR_TRACKING)
                .set(PR_TRACKING.STATUS, newStatus)
                .set(PR_TRACKING.SLA_REMAINING, toInterval(remaining))
                .setNull(PR_TRACKING.SLA_DEADLINE)
                .where(PR_TRACKING.ID.eq(id))
                .returning()
                .fetchOptional()
                .orElseThrow(() -> new IllegalStateException("PR tracking record not found for id " + id));
        return toRecord(row);
    }

    @Override
    public PrTrackingRecord resumeSla(long id, Instant newDeadline) {
        com.coreeng.supportbot.dbschema.tables.records.PrTrackingRecord row = dsl.update(PR_TRACKING)
                .set(PR_TRACKING.STATUS, PrTrackingStatus.OPEN)
                .set(PR_TRACKING.SLA_DEADLINE, newDeadline)
                .setNull(PR_TRACKING.SLA_REMAINING)
                .where(PR_TRACKING.ID.eq(id))
                .returning()
                .fetchOptional()
                .orElseThrow(() -> new IllegalStateException("PR tracking record not found for id " + id));
        return toRecord(row);
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

    @Override
    public void updateActivityTimestamps(
            long id, @Nullable Instant lastReviewAt, @Nullable Instant lastAuthorActivityAt) {
        int updated = dsl.update(PR_TRACKING)
                .set(PR_TRACKING.LAST_REVIEW_AT, lastReviewAt)
                .set(PR_TRACKING.LAST_AUTHOR_ACTIVITY_AT, lastAuthorActivityAt)
                .where(PR_TRACKING.ID.eq(id))
                .execute();
        if (updated == 0) {
            log.atWarn().addArgument(id).log("updateActivityTimestamps affected 0 rows for record {}");
        }
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
    public List<InFlightPr> findAllInFlight(@Nullable String owningTeam) {
        List<Object> binds = new ArrayList<>();
        String teamFilter = "";
        if (owningTeam != null && !owningTeam.isBlank()) {
            teamFilter = "AND pt.owning_team = ? ";
            binds.add(owningTeam);
        }
        String sql = """
                SELECT github_repo, pr_number, status, pr_created_at,
                       sla_deadline, sla_remaining, last_review_at, owning_team,
                       ticket_channel_id, ticket_query_ts, escalated_at
                FROM (
                    SELECT DISTINCT ON (pt.github_repo, pt.pr_number)
                           pt.github_repo, pt.pr_number, pt.status, pt.pr_created_at,
                           pt.sla_deadline, pt.sla_remaining, pt.last_review_at, pt.owning_team,
                           q.channel_id AS ticket_channel_id, q.ts AS ticket_query_ts,
                           (SELECT el.date FROM escalation_log el
                            WHERE el.escalation_id = pt.escalation_id AND el.event = 'opened'
                            LIMIT 1) AS escalated_at
                    FROM pr_tracking pt
                    JOIN ticket t ON t.id = pt.ticket_id
                    JOIN query q ON q.id = t.query_id
                    WHERE pt.status IN ('OPEN', 'ESCALATED', 'CHANGES_REQUESTED', 'APPROVED')
                      %s
                    ORDER BY
                        pt.github_repo,
                        pt.pr_number,
                        CASE pt.status
                            WHEN 'ESCALATED' THEN 1
                            WHEN 'OPEN' THEN 2
                            WHEN 'CHANGES_REQUESTED' THEN 3
                            WHEN 'APPROVED' THEN 4
                        END,
                        pt.sla_deadline ASC NULLS LAST
                ) deduped
                ORDER BY
                    CASE status
                        WHEN 'ESCALATED' THEN 1
                        WHEN 'OPEN' THEN 2
                        WHEN 'CHANGES_REQUESTED' THEN 3
                        WHEN 'APPROVED' THEN 4
                    END,
                    sla_deadline ASC NULLS LAST
                """.formatted(teamFilter);

        return dsl.resultQuery(sql, binds.toArray()).fetch(r -> {
            String status = checkNotNull(r.get("status", String.class));
            String waitingOn =
                    switch (status) {
                        case "OPEN", "ESCALATED" -> "TEAM";
                        case "CHANGES_REQUESTED" -> "TENANT";
                        case "APPROVED" -> "MERGE";
                        default -> "UNKNOWN";
                    };
            String repo = checkNotNull(r.get("github_repo", String.class));
            int prNumber = checkNotNull(r.get("pr_number", Integer.class));
            org.jooq.types.YearToSecond slaRemainingRaw = r.get("sla_remaining", org.jooq.types.YearToSecond.class);
            Long slaRemainingSeconds =
                    slaRemainingRaw != null ? slaRemainingRaw.toDuration().toSeconds() : null;
            return new InFlightPr(
                    repo,
                    prNumber,
                    "https://github.com/%s/pull/%d".formatted(repo, prNumber),
                    status,
                    waitingOn,
                    checkNotNull(r.get("pr_created_at", Instant.class)),
                    r.get("sla_deadline", Instant.class),
                    slaRemainingSeconds,
                    r.get("last_review_at", Instant.class),
                    checkNotNull(r.get("owning_team", String.class)),
                    checkNotNull(r.get("ticket_channel_id", String.class)),
                    checkNotNull(r.get("ticket_query_ts", String.class)),
                    r.get("escalated_at", Instant.class));
        });
    }

    @Transactional(readOnly = true)
    @Override
    public List<RepoInsights> getInsightsByRepo(@Nullable LocalDate dateFrom, @Nullable LocalDate dateTo) {
        List<Object> binds = new ArrayList<>();
        String dateFilter = buildDateFilter(dateFrom, dateTo, binds);
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
        String dateFilter = buildDateFilter(dateFrom, dateTo, binds);
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
            @Nullable LocalDate dateFrom, @Nullable LocalDate dateTo, List<Object> binds) {
        StringBuilder sb = new StringBuilder();
        if (dateFrom != null) {
            sb.append("AND pr_created_at::date >= ?::date ");
            binds.add(dateFrom);
        }
        if (dateTo != null) {
            sb.append("AND pr_created_at::date <= ?::date ");
            binds.add(dateTo);
        }
        return sb.toString();
    }

    private static YearToSecond toInterval(Duration duration) {
        long totalSeconds = duration.getSeconds();
        int days = (int) (totalSeconds / 86400);
        int hours = (int) ((totalSeconds % 86400) / 3600);
        int minutes = (int) ((totalSeconds % 3600) / 60);
        int seconds = (int) (totalSeconds % 60);
        return new YearToSecond(new YearToMonth(0), new DayToSecond(days, hours, minutes, seconds, duration.getNano()));
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
