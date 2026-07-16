package com.coreeng.supportbot.elevate;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.coreeng.supportbot.util.JsonMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter;

class ElevateRepositoryTest {
    private static final Instant ATTEMPTED_AT = Instant.parse("2026-07-13T10:00:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-07-13T10:00:05Z");

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final ElevateRepository repository =
            new ElevateRepository(jdbcTemplate, new JsonMapper().getObjectMapper());

    @Test
    void successfulEmptySnapshotAuthoritativelyClearsEveryStoredCollection() {
        repository.replaceSnapshot(new ElevateSnapshot(List.of(), List.of(), List.of()), ATTEMPTED_AT, COMPLETED_AT);

        InOrder writes = inOrder(jdbcTemplate);
        writes.verify(jdbcTemplate).update("DELETE FROM elevate_integrity_items");
        writes.verify(jdbcTemplate).update("DELETE FROM elevate_journey_users");
        writes.verify(jdbcTemplate).update("DELETE FROM elevate_journeys");
        writes.verify(jdbcTemplate).update("DELETE FROM elevate_users");
        writes.verify(jdbcTemplate).update("DELETE FROM elevate_products");
        writes.verify(jdbcTemplate)
                .update(org.mockito.ArgumentMatchers.contains("INSERT INTO elevate_integrity_items"));
        writes.verify(jdbcTemplate)
                .update(
                        org.mockito.ArgumentMatchers.contains("last_sync_succeeded = TRUE"),
                        eq(Timestamp.from(ATTEMPTED_AT)),
                        eq(Timestamp.from(COMPLETED_AT)),
                        org.mockito.ArgumentMatchers.any(java.util.UUID.class));
        verify(jdbcTemplate, never())
                .batchUpdate(
                        anyString(),
                        anyList(),
                        anyInt(),
                        org.mockito.ArgumentMatchers.<ParameterizedPreparedStatementSetter<Object>>any());
    }

    @Test
    void failedAttemptOnlyUpdatesFailureStateAndLeavesSnapshotTablesUntouched() {
        repository.recordSyncFailure(ATTEMPTED_AT, "Elevate returned HTTP 503");

        verify(jdbcTemplate)
                .update(
                        org.mockito.ArgumentMatchers.contains("last_sync_succeeded = FALSE"),
                        eq(Timestamp.from(ATTEMPTED_AT)),
                        eq("Elevate returned HTTP 503"));
        verify(jdbcTemplate, never()).update(org.mockito.ArgumentMatchers.startsWith("DELETE FROM"));
        verify(jdbcTemplate, never())
                .batchUpdate(
                        anyString(),
                        anyList(),
                        anyInt(),
                        org.mockito.ArgumentMatchers.<ParameterizedPreparedStatementSetter<Object>>any());
    }
}
