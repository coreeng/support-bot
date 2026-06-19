package com.coreeng.supportbot.enums;

import static com.coreeng.supportbot.dbschema.Tables.ESCALATION;
import static com.coreeng.supportbot.dbschema.Tables.ESCALATION_TO_TAG;
import static com.coreeng.supportbot.dbschema.Tables.IMPACT;
import static com.coreeng.supportbot.dbschema.Tables.TAG;
import static com.coreeng.supportbot.dbschema.Tables.TICKET;
import static com.coreeng.supportbot.dbschema.Tables.TICKET_TO_TAG;

import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

/** Counts stored references to soft-deleted/removed enum codes. */
@Repository
@RequiredArgsConstructor
public class OrphanReferenceRepository {

    private final DSLContext dsl;

    public long countRetiredImpactReferences() {
        return dsl.fetchCount(
                TICKET,
                TICKET.IMPACT_CODE.in(dsl.select(IMPACT.CODE).from(IMPACT).where(IMPACT.DELETED_AT.isNotNull())));
    }

    public long countRetiredTagReferences() {
        return (long) dsl.fetchCount(
                        TICKET_TO_TAG,
                        TICKET_TO_TAG.TAG_CODE.in(dsl.select(TAG.CODE).from(TAG).where(TAG.DELETED_AT.isNotNull())))
                + dsl.fetchCount(
                        ESCALATION_TO_TAG,
                        ESCALATION_TO_TAG.TAG_CODE.in(
                                dsl.select(TAG.CODE).from(TAG).where(TAG.DELETED_AT.isNotNull())));
    }

    public long countOrphanedEscalationTeamReferences(ImmutableList<String> activeEscalationCodes) {
        return dsl.fetchCount(ESCALATION, orphanedEscalationTeamCondition(activeEscalationCodes));
    }

    private static Condition orphanedEscalationTeamCondition(ImmutableList<String> activeCodes) {
        if (activeCodes.isEmpty()) {
            return ESCALATION.TEAM.isNotNull();
        }
        return ESCALATION.TEAM.isNotNull().and(ESCALATION.TEAM.notIn(activeCodes));
    }
}
