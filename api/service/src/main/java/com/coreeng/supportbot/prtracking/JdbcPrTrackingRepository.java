package com.coreeng.supportbot.prtracking;

import static com.coreeng.supportbot.dbschema.Tables.ESCALATION;
import static com.coreeng.supportbot.dbschema.Tables.PR_TRACKING;
import static com.coreeng.supportbot.dbschema.Tables.QUERY;
import static com.coreeng.supportbot.dbschema.Tables.TICKET;
import static com.coreeng.supportbot.util.JooqUtils.nullToZero;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.jooq.impl.DSL.cast;
import static org.jooq.impl.DSL.countDistinct;
import static org.jooq.impl.DSL.exists;
import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.selectOne;
import static org.jooq.impl.DSL.val;

import com.coreeng.supportbot.dbschema.enums.PrTrackingStatus;
import com.coreeng.supportbot.escalation.EscalationSource;
import com.coreeng.supportbot.prtracking.source.Provider;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.types.DayToSecond;
import org.jooq.types.YearToMonth;
import org.jooq.types.YearToSecond;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@ConditionalOnProperty(name = "pr-review-tracking.enabled", havingValue = "true")
@RequiredArgsConstructor
@Transactional
@Slf4j
public class JdbcPrTrackingRepository implements PrTrackingRepository {

    private final DSLContext dsl;
    private final PrUrlResolver urlResolver;

    @Override
    public @Nullable PrTrackingRecord insertIfAbsent(NewPrTracking newRecord) {
        com.coreeng.supportbot.dbschema.tables.records.PrTrackingRecord row = dsl.insertInto(PR_TRACKING)
                .set(PR_TRACKING.TICKET_ID, newRecord.ticketId())
                .set(PR_TRACKING.PROVIDER, newRecord.provider().storageValue())
                .set(PR_TRACKING.REPO, newRecord.repo())
                .set(PR_TRACKING.PR_NUMBER, newRecord.prNumber())
                .set(PR_TRACKING.PR_CREATED_AT, newRecord.prCreatedAt())
                .set(PR_TRACKING.SLA_DEADLINE, newRecord.slaDeadline())
                .set(PR_TRACKING.HAS_SLA, newRecord.hasSla())
                .set(PR_TRACKING.OWNING_TEAM, newRecord.owningTeam())
                .set(PR_TRACKING.CAN_AUTO_CLOSE_TICKET, newRecord.canAutoCloseTicket())
                .onConflict(PR_TRACKING.TICKET_ID, PR_TRACKING.PROVIDER, PR_TRACKING.REPO, PR_TRACKING.PR_NUMBER)
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
    public boolean existsByTicketIdAndRepoAndPrNumber(long ticketId, Provider provider, String repo, int prNumber) {
        return dsl.fetchExists(
                PR_TRACKING,
                PR_TRACKING
                        .TICKET_ID
                        .eq(ticketId)
                        .and(PR_TRACKING.PROVIDER.eq(provider.storageValue()))
                        .and(PR_TRACKING.REPO.eq(repo))
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
        // Provider is selected so commit 5's provider-aware URL builder has the data it needs;
        // in this commit every row is provider='github' and the URL builder stays GitHub-only.
        String sql = """
                SELECT provider, repo, pr_number, status, pr_created_at,
                       sla_deadline, sla_remaining, last_review_at, owning_team,
                       ticket_channel_id, ticket_query_ts, escalated_at, has_sla
                FROM (
                    SELECT DISTINCT ON (pt.provider, pt.repo, pt.pr_number)
                           pt.provider, pt.repo, pt.pr_number, pt.status, pt.pr_created_at,
                           pt.sla_deadline, pt.sla_remaining, pt.last_review_at, pt.owning_team,
                           pt.has_sla,
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
                        pt.provider,
                        pt.repo,
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
            String providerRaw = checkNotNull(r.get("provider", String.class), "provider was null");
            Provider provider = Provider.fromStorage(providerRaw);
            String repo = checkNotNull(r.get("repo", String.class), "repo was null");
            int prNumber = checkNotNull(r.get("pr_number", Integer.class), "pr_number was null for repo %s", repo);
            String status = checkNotNull(r.get("status", String.class), "status was null for %s#%s", repo, prNumber);
            String waitingOn =
                    switch (status) {
                        case "OPEN", "ESCALATED" -> "TEAM";
                        case "CHANGES_REQUESTED" -> "TENANT";
                        case "APPROVED" -> "MERGE";
                        default -> "UNKNOWN";
                    };
            org.jooq.types.YearToSecond slaRemainingRaw = r.get("sla_remaining", org.jooq.types.YearToSecond.class);
            Long slaRemainingSeconds =
                    slaRemainingRaw != null ? slaRemainingRaw.toDuration().toSeconds() : null;
            // has_sla is NOT NULL DEFAULT false in the schema (see V15__pr_tracking_has_sla.sql),
            // so a null here is a JDBC Boolean type-mapping regression or schema drift — treat it
            // the same way the insights fetch does (loud failure) rather than silently coercing to
            // false, which would hide every SLA-tracked PR from the dashboard.
            boolean hasSla =
                    checkNotNull(r.get("has_sla", Boolean.class), "has_sla was null for %s#%s", repo, prNumber);
            return new InFlightPr(
                    provider,
                    repo,
                    prNumber,
                    urlResolver.publicUrlFor(repo, prNumber),
                    status,
                    waitingOn,
                    checkNotNull(
                            r.get("pr_created_at", Instant.class), "pr_created_at was null for %s#%s", repo, prNumber),
                    r.get("sla_deadline", Instant.class),
                    slaRemainingSeconds,
                    r.get("last_review_at", Instant.class),
                    checkNotNull(r.get("owning_team", String.class), "owning_team was null for %s#%s", repo, prNumber),
                    checkNotNull(
                            r.get("ticket_channel_id", String.class),
                            "ticket_channel_id was null for %s#%s",
                            repo,
                            prNumber),
                    checkNotNull(
                            r.get("ticket_query_ts", String.class),
                            "ticket_query_ts was null for %s#%s",
                            repo,
                            prNumber),
                    r.get("escalated_at", Instant.class),
                    hasSla);
        });
    }

    @Transactional(readOnly = true)
    @Override
    public List<RepoInsights> getInsightsByRepo(@Nullable LocalDate dateFrom, @Nullable LocalDate dateTo) {
        List<Object> binds = new ArrayList<>();
        String dateFilter = buildDateFilter("pr_created_at", dateFrom, dateTo, binds);
        // Group by (provider, repo) so the same repo name across providers stays distinct in the
        // dashboard. Today every row is provider='github', so the groups collapse exactly as before.
        String sql = """
                SELECT
                    provider,
                    repo,
                    COALESCE(MIN(owning_team), 'unknown') AS owning_team,
                    COUNT(*) AS pr_count,
                    COUNT(*) FILTER (WHERE status IN ('OPEN', 'ESCALATED', 'CHANGES_REQUESTED', 'APPROVED')) AS open_count,
                    COUNT(*) FILTER (WHERE status = 'ESCALATED') AS escalated_count,
                    COUNT(*) FILTER (WHERE sla_deadline < COALESCE(closed_at, now())) AS breached_count,
                    percentile_cont(0.5) WITHIN GROUP (ORDER BY lifetime) AS p50,
                    percentile_cont(0.9) WITHIN GROUP (ORDER BY lifetime) AS p90,
                    percentile_cont(0.99) WITHIN GROUP (ORDER BY lifetime) AS p99,
                    BOOL_OR(has_sla) AS has_sla
                FROM (
                    SELECT provider, repo, owning_team, status, sla_deadline, closed_at, has_sla,
                        EXTRACT(EPOCH FROM
                            CASE WHEN closed_at IS NOT NULL THEN closed_at - pr_created_at
                                 ELSE now() - pr_created_at END
                        ) AS lifetime
                    FROM pr_tracking
                    WHERE 1=1
                      %s
                ) sub
                GROUP BY provider, repo
                ORDER BY provider, repo
                """.formatted(dateFilter);

        return dsl.resultQuery(sql, binds.toArray()).fetch(r -> {
            String providerRaw = checkNotNull(r.get("provider", String.class), "provider was null in insights row");
            Provider provider = Provider.fromStorage(providerRaw);
            String repo = checkNotNull(r.get("repo", String.class), "repo was null in insights row");
            String owningTeam =
                    checkNotNull(r.get("owning_team", String.class), "owning_team was null for repo %s", repo);
            Boolean hasSlaRaw = requireNonNullAggregate(
                    r.get("has_sla", Boolean.class),
                    "has_sla",
                    repo,
                    "BOOL_OR returns null only for an empty group, but every grouped"
                            + " repo has at least one row, so this indicates a JDBC Boolean"
                            + " type-mapping issue");
            Long prCount = requireNonNullAggregate(
                    r.get("pr_count", Long.class), "pr_count", repo, "COUNT(*) should never return null");
            Long openCount = requireNonNullAggregate(r.get("open_count", Long.class), "open_count", repo, null);
            Long escalatedCount =
                    requireNonNullAggregate(r.get("escalated_count", Long.class), "escalated_count", repo, null);
            Long breachedCount =
                    requireNonNullAggregate(r.get("breached_count", Long.class), "breached_count", repo, null);
            return new RepoInsights(
                    provider,
                    repo,
                    owningTeam,
                    prCount,
                    openCount,
                    escalatedCount,
                    breachedCount,
                    nullToZero(r.get("p50", Double.class)),
                    nullToZero(r.get("p90", Double.class)),
                    nullToZero(r.get("p99", Double.class)),
                    hasSlaRaw);
        });
    }

    @Transactional(readOnly = true)
    @Override
    public RequestBreakdown getRequestBreakdown(@Nullable LocalDate dateFrom, @Nullable LocalDate dateTo) {
        // Compares PR tickets against ALL support tickets that came in during the window, so every
        // count is bucketed by when the support request arrived (the ticket's query.date) — making
        // the PR and intervention counts true subsets of the total. PR and intervention membership
        // are EXISTS sub-selects inside COUNT(DISTINCT ...) FILTER (WHERE ...).
        Condition hasPr = exists(selectOne().from(PR_TRACKING).where(PR_TRACKING.TICKET_ID.eq(TICKET.ID)));
        Condition manuallyEscalated = exists(selectOne()
                .from(ESCALATION)
                .where(ESCALATION.TICKET_ID.eq(TICKET.ID).and(ESCALATION.SOURCE.eq(EscalationSource.manual.name()))));

        Field<Integer> totalSupport = countDistinct(TICKET.ID);
        Field<Integer> totalPr = countDistinct(TICKET.ID).filterWhere(hasPr);
        Field<Integer> interventionPr = countDistinct(TICKET.ID).filterWhere(hasPr.and(manuallyEscalated));

        return checkNotNull(dsl.select(totalSupport, totalPr, interventionPr)
                .from(TICKET)
                .join(QUERY)
                .on(QUERY.ID.eq(TICKET.QUERY_ID))
                .where(ticketCreatedBetween(dateFrom, dateTo))
                .fetchOne(r -> new RequestBreakdown(
                        r.get(totalSupport, Long.class),
                        r.get(totalPr, Long.class),
                        r.get(interventionPr, Long.class))));
    }

    /**
     * Builds an inclusive day-range predicate on the ticket-creation timestamp ({@code query.date},
     * a {@code timestamptz}) as a half-open interval ({@code date >= from AND date < to + 1 day}).
     * The bound — not the column — is cast, so the predicate stays sargable (an index on
     * {@code query.date} can be used), and the {@code + 1 day} is computed on the {@link LocalDate}
     * rather than as SQL interval arithmetic.
     */
    private static Condition ticketCreatedBetween(@Nullable LocalDate dateFrom, @Nullable LocalDate dateTo) {
        Condition condition = noCondition();
        if (dateFrom != null) {
            condition = condition.and(QUERY.DATE.ge(cast(val(dateFrom), QUERY.DATE.getDataType())));
        }
        if (dateTo != null) {
            condition = condition.and(QUERY.DATE.lt(cast(val(dateTo.plusDays(1)), QUERY.DATE.getDataType())));
        }
        return condition;
    }

    private static <T> T requireNonNullAggregate(
            @Nullable T value, String column, String repo, @Nullable String extra) {
        if (value != null) {
            return value;
        }
        log.atError().addArgument(column).addArgument(repo).log("{} null for repo {} — aborting insights fetch");
        String msg = column + " was null for repo " + repo;
        if (extra != null) {
            msg += " — " + extra;
        }
        throw new DataIntegrityViolationException(msg);
    }

    /**
     * Builds an inclusive day-range predicate on a {@code timestamptz} column as a half-open
     * interval ({@code col >= from AND col < to + 1 day}). This keeps the predicate sargable so an
     * index on {@code column} can be used (a {@code col::date} cast cannot), and compares the raw
     * timestamp instead of truncating it in the DB session timezone, so rows near a day boundary
     * land in the expected day regardless of server timezone.
     *
     * <p>{@code column} is a trusted compile-time constant supplied by the caller, never user input.
     */
    private static String buildDateFilter(
            String column, @Nullable LocalDate dateFrom, @Nullable LocalDate dateTo, List<Object> binds) {
        StringBuilder sb = new StringBuilder();
        if (dateFrom != null) {
            sb.append("AND ").append(column).append(" >= ?::date ");
            binds.add(dateFrom);
        }
        if (dateTo != null) {
            sb.append("AND ").append(column).append(" < ?::date + interval '1 day' ");
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
                Provider.fromStorage(checkNotNull(row.getProvider())),
                checkNotNull(row.getRepo()),
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
