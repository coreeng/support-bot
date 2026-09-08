package com.coreeng.supportbot.summary;

import static com.coreeng.supportbot.dbschema.Tables.SUMMARY_SNAPSHOT;
import static org.jooq.impl.DSL.excluded;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class JdbcSummarySnapshotRepository implements SummarySnapshotRepository {

    private final DSLContext dsl;

    @Override
    @Transactional(readOnly = true)
    public @Nullable SummarySnapshot find(SummaryWindow window, String promptId) {
        return dsl.selectFrom(SUMMARY_SNAPSHOT)
                .where(SUMMARY_SNAPSHOT.WINDOW_FROM.eq(window.from()))
                .and(SUMMARY_SNAPSHOT.WINDOW_TO.eq(window.to()))
                .and(SUMMARY_SNAPSHOT.PROMPT_ID.eq(promptId))
                .fetchOne(r -> new SummarySnapshot(
                        new SummaryWindow(r.get(SUMMARY_SNAPSHOT.WINDOW_FROM), r.get(SUMMARY_SNAPSHOT.WINDOW_TO)),
                        r.get(SUMMARY_SNAPSHOT.PROMPT_ID),
                        r.get(SUMMARY_SNAPSHOT.FINGERPRINT),
                        r.get(SUMMARY_SNAPSHOT.CONTENT),
                        r.get(SUMMARY_SNAPSHOT.MODEL),
                        r.get(SUMMARY_SNAPSHOT.GENERATED_AT)));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Upserts on the (window_from, window_to, prompt_id) unique key: a regenerated summary
     * replaces the stale one in place rather than accumulating history, and two visitors racing to
     * write the same window cannot deadlock on a duplicate key.
     */
    @Override
    @Transactional
    public void upsert(SummarySnapshot snapshot) {
        dsl.insertInto(
                        SUMMARY_SNAPSHOT,
                        SUMMARY_SNAPSHOT.WINDOW_FROM,
                        SUMMARY_SNAPSHOT.WINDOW_TO,
                        SUMMARY_SNAPSHOT.PROMPT_ID,
                        SUMMARY_SNAPSHOT.FINGERPRINT,
                        SUMMARY_SNAPSHOT.CONTENT,
                        SUMMARY_SNAPSHOT.MODEL)
                .values(
                        snapshot.window().from(),
                        snapshot.window().to(),
                        snapshot.promptId(),
                        snapshot.fingerprint(),
                        snapshot.content(),
                        snapshot.model())
                .onConflict(SUMMARY_SNAPSHOT.WINDOW_FROM, SUMMARY_SNAPSHOT.WINDOW_TO, SUMMARY_SNAPSHOT.PROMPT_ID)
                .doUpdate()
                .set(SUMMARY_SNAPSHOT.FINGERPRINT, excluded(SUMMARY_SNAPSHOT.FINGERPRINT))
                .set(SUMMARY_SNAPSHOT.CONTENT, excluded(SUMMARY_SNAPSHOT.CONTENT))
                .set(SUMMARY_SNAPSHOT.MODEL, excluded(SUMMARY_SNAPSHOT.MODEL))
                .set(SUMMARY_SNAPSHOT.GENERATED_AT, org.jooq.impl.DSL.currentInstant())
                .execute();
    }
}
