package com.coreeng.supportbot.analysis;

import static com.coreeng.supportbot.dbschema.Tables.ANALYSIS_PROMPT;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class JdbcAnalysisPromptRepository implements AnalysisPromptRepository {

    private final DSLContext dsl;

    @Override
    @Transactional(readOnly = true)
    public @Nullable AnalysisPrompt findInUse(AnalysisPromptType type) {
        return dsl.selectFrom(ANALYSIS_PROMPT)
                .where(ANALYSIS_PROMPT.IS_IN_USE.isTrue())
                .and(ANALYSIS_PROMPT.TYPE.eq(type.dbValue()))
                .fetchOne(r -> new AnalysisPrompt(r.get(ANALYSIS_PROMPT.VERSION), r.get(ANALYSIS_PROMPT.CONTENT)));
    }
}
