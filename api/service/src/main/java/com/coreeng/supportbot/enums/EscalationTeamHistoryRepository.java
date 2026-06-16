package com.coreeng.supportbot.enums;

import static com.coreeng.supportbot.dbschema.Tables.ESCALATION_TEAM;
import static org.jooq.impl.DSL.excluded;
import static org.jooq.impl.DSL.row;

import com.google.common.collect.ImmutableList;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

/**
 * Persistent code+label history for escalation teams (PT-518). Active teams (with group-ref /
 * Slack mention) come from config via {@link EnumsService}; this store exists only so that a
 * renamed/removed escalation team code still resolves to a label on existing tickets/escalations.
 */
@Repository
@RequiredArgsConstructor
public class EscalationTeamHistoryRepository {
    private final DSLContext dsl;

    /** Returns the label for {@code code} including soft-deleted (retired) rows, or null if unknown. */
    @Nullable public String findLabelByCode(String code) {
        return dsl.select(ESCALATION_TEAM.LABEL)
                .from(ESCALATION_TEAM)
                .where(ESCALATION_TEAM.CODE.eq(code))
                .fetchOne(r -> r.get(ESCALATION_TEAM.LABEL));
    }

    public int insertOrActivate(ImmutableList<EscalationTeam> teams) {
        if (teams.isEmpty()) {
            return 0;
        }
        return dsl.insertInto(ESCALATION_TEAM, ESCALATION_TEAM.CODE, ESCALATION_TEAM.LABEL)
                .valuesOfRows(teams.stream().map(t -> row(t.code(), t.label())).toList())
                .onConflict(ESCALATION_TEAM.CODE)
                .doUpdate()
                .set(ESCALATION_TEAM.LABEL, excluded(ESCALATION_TEAM.LABEL))
                .setNull(ESCALATION_TEAM.DELETED_AT)
                .execute();
    }

    /** Soft-deletes every team whose code is not in {@code codes}. When {@code codes} is empty, soft-deletes all. */
    public int deleteAllExcept(ImmutableList<String> codes) {
        if (codes.isEmpty()) {
            return dsl.update(ESCALATION_TEAM)
                    .set(ESCALATION_TEAM.DELETED_AT, Instant.now())
                    .execute();
        }
        return dsl.update(ESCALATION_TEAM)
                .set(ESCALATION_TEAM.DELETED_AT, Instant.now())
                .where(ESCALATION_TEAM.CODE.notIn(codes))
                .execute();
    }
}
