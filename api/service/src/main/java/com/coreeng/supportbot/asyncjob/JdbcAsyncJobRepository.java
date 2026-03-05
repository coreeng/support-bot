package com.coreeng.supportbot.asyncjob;

import static com.coreeng.supportbot.dbschema.Tables.ASYNC_JOB;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
@Slf4j
public class JdbcAsyncJobRepository implements AsyncJobRepository {

    private final DSLContext dsl;

    @Override
    @Transactional
    public boolean tryStartJob(String id, String data) {
        try {
            dsl.insertInto(ASYNC_JOB)
                    .set(ASYNC_JOB.ID, id)
                    .set(ASYNC_JOB.DATA, data)
                    .set(ASYNC_JOB.STARTED_AT, Instant.now())
                    .execute();
            return true;
        } catch (DuplicateKeyException e) {
            // Unique constraint violation
            log.debug("Batch {} already exists", id);
            return false;
        }
    }

    @Override
    @Transactional
    public void deleteJob(String id) {
        dsl.deleteFrom(ASYNC_JOB).where(ASYNC_JOB.ID.eq(id)).execute();
    }

    @Override
    @Transactional(readOnly = true)
    public AsyncJobRepository.@Nullable AsyncJob findJob(String id) {
        return dsl.selectFrom(ASYNC_JOB)
                .where(ASYNC_JOB.ID.eq(id))
                .fetchOne(r -> new AsyncJob(r.get(ASYNC_JOB.ID), r.get(ASYNC_JOB.DATA), r.get(ASYNC_JOB.STARTED_AT)));
    }
}
