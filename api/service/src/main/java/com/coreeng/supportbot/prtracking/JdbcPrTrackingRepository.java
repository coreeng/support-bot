package com.coreeng.supportbot.prtracking;

import static com.coreeng.supportbot.dbschema.Tables.PR_TRACKING;
import static com.google.common.base.Preconditions.checkNotNull;

import com.coreeng.supportbot.dbschema.enums.PrTrackingStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
@Transactional
public class JdbcPrTrackingRepository implements PrTrackingRepository {

    private final DSLContext dsl;

    @Override
    public PrTrackingRecord insert(NewPrTracking newRecord) {
        com.coreeng.supportbot.dbschema.tables.records.PrTrackingRecord row =
                dsl.insertInto(PR_TRACKING)
                        .set(PR_TRACKING.TICKET_ID, newRecord.ticketId())
                        .set(PR_TRACKING.GITHUB_REPO, newRecord.githubRepo())
                        .set(PR_TRACKING.PR_NUMBER, newRecord.prNumber())
                        .set(PR_TRACKING.PR_CREATED_AT, newRecord.prCreatedAt())
                        .set(PR_TRACKING.SLA_DEADLINE, newRecord.slaDeadline())
                        .set(PR_TRACKING.OWNING_TEAM, newRecord.owningTeam())
                        .returning()
                        .fetchSingle();
        return toRecord(row);
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
    public boolean existsByTicketIdAndRepoAndPrNumber(long ticketId, String githubRepo, int prNumber) {
        return dsl.fetchExists(
                PR_TRACKING,
                PR_TRACKING.TICKET_ID.eq(ticketId)
                        .and(PR_TRACKING.GITHUB_REPO.eq(githubRepo))
                        .and(PR_TRACKING.PR_NUMBER.eq(prNumber)));
    }

    private static PrTrackingRecord toRecord(
            com.coreeng.supportbot.dbschema.tables.records.PrTrackingRecord row) {
        return new PrTrackingRecord(
                checkNotNull(row.getId()),
                checkNotNull(row.getTicketId()),
                checkNotNull(row.getGithubRepo()),
                checkNotNull(row.getPrNumber()),
                checkNotNull(row.getPrCreatedAt()),
                checkNotNull(row.getSlaDeadline()),
                checkNotNull(row.getOwningTeam()),
                checkNotNull(row.getStatus()),
                row.getEscalationId(),
                row.getClosedAt());
    }
}
