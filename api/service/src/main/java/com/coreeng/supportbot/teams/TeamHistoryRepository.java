package com.coreeng.supportbot.teams;

import static com.coreeng.supportbot.dbschema.Tables.TEAM;
import static org.jooq.impl.DSL.excluded;
import static org.jooq.impl.DSL.row;

import com.google.common.collect.ImmutableList;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TeamHistoryRepository {
    private final DSLContext dsl;

    @Nullable public String findLabelByCode(String code) {
        return dsl.select(TEAM.LABEL).from(TEAM).where(TEAM.CODE.eq(code)).fetchOne(r -> r.get(TEAM.LABEL));
    }

    public int insertOrActivate(ImmutableList<Team> teams) {
        if (teams.isEmpty()) {
            return 0;
        }
        return dsl.insertInto(TEAM, TEAM.CODE, TEAM.LABEL)
                .valuesOfRows(teams.stream().map(t -> row(t.code(), t.label())).toList())
                .onConflict(TEAM.CODE)
                .doUpdate()
                .set(TEAM.LABEL, excluded(TEAM.LABEL))
                .setNull(TEAM.DELETED_AT)
                .execute();
    }

    public int deleteAllExcept(ImmutableList<String> codes) {
        if (codes.isEmpty()) {
            return dsl.update(TEAM).set(TEAM.DELETED_AT, Instant.now()).execute();
        }
        return dsl.update(TEAM)
                .set(TEAM.DELETED_AT, Instant.now())
                .where(TEAM.CODE.notIn(codes))
                .execute();
    }
}
