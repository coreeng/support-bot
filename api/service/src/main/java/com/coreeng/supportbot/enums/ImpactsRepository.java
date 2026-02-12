package com.coreeng.supportbot.enums;

import static com.coreeng.supportbot.dbschema.Tables.IMPACT;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static org.jooq.impl.DSL.excluded;
import static org.jooq.impl.DSL.row;

import com.google.common.collect.ImmutableList;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.ResultQuery;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImpactsRepository {
    private final DSLContext dsl;

    public ImmutableList<TicketImpact> listAll() {
        return fetchImpacts(dsl.select(IMPACT.LABEL, IMPACT.CODE).from(IMPACT));
    }

    public ImmutableList<TicketImpact> listAllActive() {
        return fetchImpacts(dsl.select(IMPACT.LABEL, IMPACT.CODE).from(IMPACT).where(IMPACT.DELETED_AT.isNull()));
    }

    @Nullable public TicketImpact findImpactByCode(String code) {
        return dsl.select(IMPACT.LABEL, IMPACT.CODE)
                .from(IMPACT)
                .where(IMPACT.CODE.eq(code))
                .fetchOne(r -> new TicketImpact(r.get(IMPACT.LABEL), r.get(IMPACT.CODE)));
    }

    public int insertOrActivate(ImmutableList<TicketImpact> impacts) {
        if (impacts.isEmpty()) {
            return 0;
        }
        return dsl.insertInto(IMPACT, IMPACT.LABEL, IMPACT.CODE)
                .valuesOfRows(
                        impacts.stream().map(i -> row(i.label(), i.code())).toList())
                .onConflict(IMPACT.CODE)
                .doUpdate()
                .set(IMPACT.LABEL, excluded(IMPACT.LABEL))
                .setNull(IMPACT.DELETED_AT)
                .execute();
    }

    public int deleteAllExcept(ImmutableList<String> codes) {
        if (codes.isEmpty()) {
            return 0;
        }
        return dsl.update(IMPACT)
                .set(IMPACT.DELETED_AT, Instant.now())
                .where(IMPACT.CODE.notIn(codes))
                .execute();
    }

    private ImmutableList<TicketImpact> fetchImpacts(ResultQuery<?> query) {
        try (var stream = query.stream()) {
            return stream.map(r -> new TicketImpact(r.get(IMPACT.LABEL), r.get(IMPACT.CODE)))
                    .collect(toImmutableList());
        }
    }
}
