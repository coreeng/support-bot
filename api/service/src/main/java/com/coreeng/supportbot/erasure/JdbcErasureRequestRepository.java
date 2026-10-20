package com.coreeng.supportbot.erasure;

import static com.coreeng.supportbot.dbschema.Tables.ERASURE_REQUESTS;

import com.coreeng.supportbot.dbschema.tables.records.ErasureRequestsRecord;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class JdbcErasureRequestRepository implements ErasureRequestRepository {

    private final DSLContext dsl;

    @Override
    @Transactional
    public boolean insertIfAbsent(ErasureRequest request) {
        return dsl.insertInto(ERASURE_REQUESTS)
                        .set(ERASURE_REQUESTS.ID, request.id())
                        .set(ERASURE_REQUESTS.CURRENT_USER_EMAIL, request.currentUserEmail())
                        .set(ERASURE_REQUESTS.NEW_USER_EMAIL, request.newUserEmail())
                        .set(ERASURE_REQUESTS.STATUS, request.status().name())
                        .set(ERASURE_REQUESTS.CREATED_AT, request.requestedAt())
                        .onConflict(ERASURE_REQUESTS.ID)
                        .doNothing()
                        .execute()
                == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ErasureRequest> findById(UUID id) {
        return dsl.selectFrom(ERASURE_REQUESTS)
                .where(ERASURE_REQUESTS.ID.eq(id))
                .fetchOptional(JdbcErasureRequestRepository::toErasureRequest);
    }

    private static ErasureRequest toErasureRequest(ErasureRequestsRecord row) {
        return new ErasureRequest(
                row.getId(),
                row.getCurrentUserEmail(),
                row.getNewUserEmail(),
                ErasureRequestStatus.valueOf(row.getStatus()),
                row.getCreatedAt());
    }
}
