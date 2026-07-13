package com.coreeng.supportbot.elevate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ElevateRepository {
    private static final int INSERT_BATCH_SIZE = 500;
    private static final String SELECT_STATE = """
            SELECT last_ping_attempt_at,
                   last_ping_success_at,
                   last_ping_succeeded,
                   last_ping_error,
                   last_sync_attempt_at,
                   last_sync_success_at,
                   last_sync_succeeded,
                   last_sync_error
              FROM elevate_sync_state
             WHERE singleton = TRUE
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ElevateStoredStatus getStoredStatus() {
        return new ElevateStoredStatus(readState(), readSnapshot());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ElevateSnapshot getSnapshot() {
        return readSnapshot();
    }

    @Transactional(readOnly = true)
    public ElevateSyncState getState() {
        return readState();
    }

    private ElevateSnapshot readSnapshot() {
        List<ElevateProduct> products = readResources("elevate_products", ElevateProduct.class);
        List<ElevateUser> users = readResources("elevate_users", ElevateUser.class);
        List<ElevateJourney> journeys = readResources("elevate_journeys", ElevateJourney.class);
        return new ElevateSnapshot(products, users, journeys);
    }

    private ElevateSyncState readState() {
        List<ElevateSyncState> states = jdbcTemplate.query(
                SELECT_STATE,
                (resultSet, rowNumber) -> new ElevateSyncState(
                        toInstant(resultSet.getTimestamp("last_ping_attempt_at")),
                        toInstant(resultSet.getTimestamp("last_ping_success_at")),
                        resultSet.getObject("last_ping_succeeded", Boolean.class),
                        resultSet.getString("last_ping_error"),
                        toInstant(resultSet.getTimestamp("last_sync_attempt_at")),
                        toInstant(resultSet.getTimestamp("last_sync_success_at")),
                        resultSet.getObject("last_sync_succeeded", Boolean.class),
                        resultSet.getString("last_sync_error")));
        return states.isEmpty() ? ElevateSyncState.empty() : states.getFirst();
    }

    @Transactional
    public void replaceSnapshot(ElevateSnapshot snapshot, Instant attemptedAt, Instant completedAt) {
        jdbcTemplate.update("DELETE FROM elevate_journeys");
        jdbcTemplate.update("DELETE FROM elevate_users");
        jdbcTemplate.update("DELETE FROM elevate_products");

        insertProducts(snapshot.products());
        insertUsers(snapshot.users());
        insertJourneys(snapshot.journeys());

        jdbcTemplate.update("""
                UPDATE elevate_sync_state
                   SET last_sync_attempt_at = ?,
                       last_sync_success_at = ?,
                       last_sync_succeeded = TRUE,
                       last_sync_error = NULL
                 WHERE singleton = TRUE
                """, Timestamp.from(attemptedAt), Timestamp.from(completedAt));
    }

    public void recordSyncFailure(Instant attemptedAt, String error) {
        jdbcTemplate.update("""
                UPDATE elevate_sync_state
                   SET last_sync_attempt_at = ?,
                       last_sync_succeeded = FALSE,
                       last_sync_error = ?
                 WHERE singleton = TRUE
                """, Timestamp.from(attemptedAt), error);
    }

    public void recordPingSuccess(Instant attemptedAt, Instant completedAt) {
        jdbcTemplate.update("""
                UPDATE elevate_sync_state
                   SET last_ping_attempt_at = ?,
                       last_ping_success_at = ?,
                       last_ping_succeeded = TRUE,
                       last_ping_error = NULL
                 WHERE singleton = TRUE
                """, Timestamp.from(attemptedAt), Timestamp.from(completedAt));
    }

    public void recordPingFailure(Instant attemptedAt, String error) {
        jdbcTemplate.update("""
                UPDATE elevate_sync_state
                   SET last_ping_attempt_at = ?,
                       last_ping_succeeded = FALSE,
                       last_ping_error = ?
                 WHERE singleton = TRUE
                """, Timestamp.from(attemptedAt), error);
    }

    private <T> List<T> readResources(String table, Class<T> type) {
        String sql = "SELECT payload::text FROM " + table + " ORDER BY resource_id";
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> fromJson(resultSet.getString(1), type));
    }

    private void insertProducts(List<ElevateProduct> products) {
        batchInsert(
                "INSERT INTO elevate_products (resource_id, payload) VALUES (?, CAST(? AS jsonb))",
                products,
                ElevateProduct::id);
    }

    private void insertUsers(List<ElevateUser> users) {
        if (users.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO elevate_users (resource_id, payload) VALUES (?, CAST(? AS jsonb))";
        jdbcTemplate.batchUpdate(sql, users, INSERT_BATCH_SIZE, (statement, user) -> {
            statement.setObject(1, user.id());
            statement.setString(2, toJson(user));
        });
    }

    private void insertJourneys(List<ElevateJourney> journeys) {
        batchInsert(
                "INSERT INTO elevate_journeys (resource_id, payload) VALUES (?, CAST(? AS jsonb))",
                journeys,
                ElevateJourney::id);
    }

    private <T> void batchInsert(String sql, List<T> resources, Function<T, String> idExtractor) {
        if (resources.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(sql, resources, INSERT_BATCH_SIZE, (statement, resource) -> {
            statement.setString(1, idExtractor.apply(resource));
            statement.setString(2, toJson(resource));
        });
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ElevateApiException("Could not serialize Elevate snapshot", e);
        }
    }

    private <T> T fromJson(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException e) {
            throw new ElevateApiException("Could not deserialize stored Elevate snapshot", e);
        }
    }

    private static @Nullable Instant toInstant(@Nullable Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
