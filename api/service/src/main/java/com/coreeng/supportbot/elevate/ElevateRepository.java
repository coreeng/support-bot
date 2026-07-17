package com.coreeng.supportbot.elevate;

import com.coreeng.supportbot.util.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
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
    private static final String PRODUCT_SUMMARY = """
            SELECT p.resource_id,
                   p.slug,
                   p.name,
                   p.customer,
                   p.created_at,
                   p.last_updated_at,
                   (SELECT COUNT(*) FROM elevate_journeys j WHERE j.product_id = p.resource_id) AS journey_count,
                   (SELECT COUNT(*) FROM elevate_users u WHERE u.product_id = p.resource_id) AS user_count,
                   (SELECT COUNT(*)
                      FROM elevate_journeys j
                      JOIN elevate_journey_users ju ON ju.journey_id = j.resource_id
                      JOIN elevate_users u ON u.resource_id = ju.user_id AND u.product_id = j.product_id
                     WHERE j.product_id = p.resource_id) AS assignment_count
              FROM elevate_products p
            """;
    private static final String JOURNEY_SUMMARY = """
            SELECT j.resource_id,
                   j.slug,
                   j.name,
                   j.product_id,
                   j.product_slug,
                   j.user_description,
                   j.primary_problems,
                   j.created_at,
                   j.last_updated_at,
                   (SELECT COUNT(*)
                      FROM elevate_journey_users ju
                      JOIN elevate_users u ON u.resource_id = ju.user_id AND u.product_id = j.product_id
                     WHERE ju.journey_id = j.resource_id) AS user_count,
                   (SELECT COUNT(*)
                      FROM elevate_journey_users ju
                      LEFT JOIN elevate_users u ON u.resource_id = ju.user_id
                     WHERE ju.journey_id = j.resource_id
                       AND u.resource_id IS NULL) AS missing_user_count,
                   (SELECT COUNT(*)
                      FROM elevate_journey_users ju
                      JOIN elevate_users u ON u.resource_id = ju.user_id
                     WHERE ju.journey_id = j.resource_id
                       AND u.product_id <> j.product_id) AS cross_product_user_count
              FROM elevate_journeys j
            """;
    private static final String USER_SUMMARY = """
            SELECT u.resource_id,
                   u.product_id,
                   u.name,
                   u.description,
                   u.created_at,
                   u.last_updated_at,
                   (SELECT COUNT(*)
                      FROM elevate_journey_users ju
                      JOIN elevate_journeys j ON j.resource_id = ju.journey_id AND j.product_id = u.product_id
                     WHERE ju.user_id = u.resource_id) AS journey_count
              FROM elevate_users u
            """;
    private static final String REFRESH_INTEGRITY_ITEMS = """
            INSERT INTO elevate_integrity_items
                   (type, journey_id, journey_name, journey_product_id, user_id, user_name, user_product_id,
                    sort_name, sort_id, search_text)
            SELECT type, journey_id, journey_name, journey_product_id, user_id, user_name, user_product_id,
                   sort_name, sort_id, search_text
              FROM elevate_integrity_item_source
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ElevateStoredStatus getStoredStatus() {
        @Nullable UUID snapshotVersion = jdbcTemplate.queryForObject(
                "SELECT snapshot_version FROM elevate_sync_state WHERE singleton = TRUE", UUID.class);
        return new ElevateStoredStatus(readState(), snapshotVersion, readCounts(), readIntegrityCounts());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ElevateSnapshot getSnapshot() {
        return readSnapshot();
    }

    @Transactional(readOnly = true)
    public ElevateSyncState getState() {
        return readState();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<ElevateProductSummary> findProducts(UUID snapshotVersion, ElevateReadQuery query) {
        requireSnapshotVersion(snapshotVersion);
        String base = "SELECT resource.*, (journey_count + user_count) AS relationship_count FROM (" + PRODUCT_SUMMARY
                + ") resource";
        return pageSummaries(base, productSearch(query.query()), query, PRODUCT_MAPPER);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<ElevateProductSummary> findProduct(UUID snapshotVersion, String productId) {
        requireSnapshotVersion(snapshotVersion);
        return jdbcTemplate
                .query(
                        "SELECT resource.*, (journey_count + user_count) AS relationship_count FROM (" + PRODUCT_SUMMARY
                                + ") resource WHERE resource_id = ?",
                        PRODUCT_MAPPER,
                        productId)
                .stream()
                .findFirst();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<ElevateJourneySummary> findProductJourneys(
            UUID snapshotVersion, String productId, ElevateReadQuery query) {
        requireSnapshotVersion(snapshotVersion);
        requireResource("elevate_products", productId, "product");
        String base = "SELECT resource.*, user_count AS relationship_count FROM (" + JOURNEY_SUMMARY
                + ") resource WHERE product_id = ?";
        return pageSummaries(base, List.of(productId), journeySearch(query.query()), query, JOURNEY_MAPPER);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<ElevateUserSummary> findProductUsers(UUID snapshotVersion, String productId, ElevateReadQuery query) {
        requireSnapshotVersion(snapshotVersion);
        requireResource("elevate_products", productId, "product");
        String base = "SELECT resource.*, journey_count AS relationship_count FROM (" + USER_SUMMARY
                + ") resource WHERE product_id = ?";
        return pageSummaries(base, List.of(productId), userSearch(query.query()), query, USER_MAPPER);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<ElevateJourneySummary> findJourney(UUID snapshotVersion, String journeyId) {
        requireSnapshotVersion(snapshotVersion);
        return jdbcTemplate
                .query(
                        "SELECT resource.*, user_count AS relationship_count FROM (" + JOURNEY_SUMMARY
                                + ") resource WHERE resource_id = ?",
                        JOURNEY_MAPPER,
                        journeyId)
                .stream()
                .findFirst();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<ElevateUserSummary> findJourneyUsers(UUID snapshotVersion, String journeyId, ElevateReadQuery query) {
        requireSnapshotVersion(snapshotVersion);
        requireResource("elevate_journeys", journeyId, "journey");
        String base = "SELECT resource.*, journey_count AS relationship_count FROM (" + USER_SUMMARY
                + ") resource JOIN elevate_journey_users relation ON relation.user_id = resource.resource_id"
                + " JOIN elevate_journeys parent ON parent.resource_id = relation.journey_id"
                + " WHERE relation.journey_id = ? AND resource.product_id = parent.product_id";
        return pageSummaries(base, List.of(journeyId), userSearch(query.query()), query, USER_MAPPER);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<ElevateUserSummary> findUser(UUID snapshotVersion, UUID userId) {
        requireSnapshotVersion(snapshotVersion);
        return jdbcTemplate
                .query(
                        "SELECT resource.*, journey_count AS relationship_count FROM (" + USER_SUMMARY
                                + ") resource WHERE resource_id = ?",
                        USER_MAPPER,
                        userId)
                .stream()
                .findFirst();
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<ElevateJourneySummary> findUserJourneys(UUID snapshotVersion, UUID userId, ElevateReadQuery query) {
        requireSnapshotVersion(snapshotVersion);
        requireResource("elevate_users", userId, "user");
        String base = "SELECT resource.*, user_count AS relationship_count FROM (" + JOURNEY_SUMMARY
                + ") resource JOIN elevate_journey_users relation ON relation.journey_id = resource.resource_id"
                + " JOIN elevate_users parent ON parent.resource_id = relation.user_id"
                + " WHERE relation.user_id = ? AND resource.product_id = parent.product_id";
        return pageSummaries(base, List.of(userId), journeySearch(query.query()), query, JOURNEY_MAPPER);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<ElevateIntegrityItem> findIntegrity(
            UUID snapshotVersion, ElevateIntegrityType type, ElevateReadQuery query) {
        requireSnapshotVersion(snapshotVersion);
        String base = "SELECT integrity.*, 0 AS relationship_count FROM elevate_integrity_items integrity";
        List<Object> parameters = new ArrayList<>();
        List<String> conditions = new ArrayList<>();
        if (type != ElevateIntegrityType.ALL) {
            conditions.add("type = ?");
            parameters.add(type.databaseValue());
        }
        if (!query.query().isBlank()) {
            conditions.add("search_text LIKE ? ESCAPE '!'");
            parameters.add(searchPattern(query.query()));
        }
        String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        String order = query.sort() == ElevateSort.RELATIONSHIPS
                ? " ORDER BY type " + query.direction().sql() + ", sort_name ASC, sort_id ASC"
                : " ORDER BY sort_name " + query.direction().sql() + ", sort_id "
                        + query.direction().sql() + ", type ASC";
        return page(base + where, parameters, order, query, INTEGRITY_MAPPER);
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

    private ElevateCounts readCounts() {
        return Objects.requireNonNull(
                jdbcTemplate.queryForObject(
                        """
                SELECT (SELECT COUNT(*) FROM elevate_products) AS products,
                       (SELECT COUNT(*) FROM elevate_journeys) AS journeys,
                       (SELECT COUNT(*) FROM elevate_users) AS users,
                       (SELECT COUNT(*) FROM elevate_journey_users) AS assignments
                """,
                        (resultSet, rowNumber) -> new ElevateCounts(
                                resultSet.getLong("products"),
                                resultSet.getLong("journeys"),
                                resultSet.getLong("users"),
                                resultSet.getLong("assignments"))),
                "Elevate counts query returned no row");
    }

    private ElevateIntegrityCounts readIntegrityCounts() {
        return Objects.requireNonNull(
                jdbcTemplate.queryForObject(
                        """
                SELECT COUNT(*) FILTER (WHERE type = 'ORPHAN_JOURNEY') AS orphan_journeys,
                       COUNT(*) FILTER (WHERE type = 'ORPHAN_USER') AS orphan_users,
                       COUNT(*) FILTER (WHERE type = 'MISSING_ASSIGNMENT') AS missing_assignments,
                       COUNT(*) FILTER (WHERE type = 'CROSS_PRODUCT_ASSIGNMENT') AS cross_product_assignments
                  FROM elevate_integrity_items
                """,
                        (resultSet, rowNumber) -> new ElevateIntegrityCounts(
                                resultSet.getLong("orphan_journeys"),
                                resultSet.getLong("orphan_users"),
                                resultSet.getLong("missing_assignments"),
                                resultSet.getLong("cross_product_assignments"))),
                "Elevate integrity counts query returned no row");
    }

    @Transactional
    public void replaceSnapshot(ElevateSnapshot snapshot, Instant attemptedAt, Instant completedAt) {
        UUID snapshotVersion = UUID.randomUUID();
        jdbcTemplate.update("DELETE FROM elevate_integrity_items");
        jdbcTemplate.update("DELETE FROM elevate_journey_users");
        jdbcTemplate.update("DELETE FROM elevate_journeys");
        jdbcTemplate.update("DELETE FROM elevate_users");
        jdbcTemplate.update("DELETE FROM elevate_products");

        insertProducts(snapshot.products(), snapshot.productPayloads());
        insertUsers(snapshot.users(), snapshot.userPayloads());
        insertJourneys(snapshot.journeys(), snapshot.journeyPayloads());
        insertJourneyUsers(snapshot.journeys());
        jdbcTemplate.update(REFRESH_INTEGRITY_ITEMS);

        jdbcTemplate.update("""
                UPDATE elevate_sync_state
                   SET last_sync_attempt_at = ?,
                       last_sync_success_at = ?,
                       last_sync_succeeded = TRUE,
                       last_sync_error = NULL,
                       snapshot_version = ?
                 WHERE singleton = TRUE
                """, Timestamp.from(attemptedAt), Timestamp.from(completedAt), snapshotVersion);
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

    private <T> Page<T> pageSummaries(
            String base,
            List<Object> baseParameters,
            SearchClause search,
            ElevateReadQuery query,
            RowMapper<T> mapper) {
        List<Object> parameters = new ArrayList<>(baseParameters);
        String pageableBase = "SELECT pageable.* FROM (" + base + ") pageable";
        StringBuilder where = new StringBuilder(" WHERE ");
        where.append(search.sql());
        parameters.addAll(search.parameters());
        switch (query.relationship()) {
            case LINKED -> where.append(" AND relationship_count > 0");
            case UNASSIGNED -> where.append(" AND relationship_count = 0");
            case ALL -> {
                // No relationship predicate.
            }
        }
        String direction = query.direction().sql();
        String order = query.sort() == ElevateSort.RELATIONSHIPS
                ? " ORDER BY relationship_count " + direction + ", LOWER(name) ASC, resource_id ASC"
                : " ORDER BY LOWER(name) " + direction + ", resource_id " + direction;
        return page(pageableBase + where, parameters, order, query, mapper);
    }

    private <T> Page<T> pageSummaries(String base, SearchClause search, ElevateReadQuery query, RowMapper<T> mapper) {
        return pageSummaries(base, List.of(), search, query, mapper);
    }

    private <T> Page<T> page(
            String filteredSql, List<Object> parameters, String order, ElevateReadQuery query, RowMapper<T> mapper) {
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (" + filteredSql + ") filtered", Long.class, parameters.toArray());
        long totalElements = total == null ? 0 : total;
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(query.pageSize());
        pageParameters.add((long) query.page() * query.pageSize());
        List<T> content =
                jdbcTemplate.query(filteredSql + order + " LIMIT ? OFFSET ?", mapper, pageParameters.toArray());
        long totalPages = totalElements == 0 ? 0 : (totalElements + query.pageSize() - 1) / query.pageSize();
        return new Page<>(ImmutableList.copyOf(content), query.page(), totalPages, totalElements);
    }

    private void requireSnapshotVersion(UUID expected) {
        @Nullable UUID current = jdbcTemplate.queryForObject(
                "SELECT snapshot_version FROM elevate_sync_state WHERE singleton = TRUE", UUID.class);
        if (!expected.equals(current)) {
            throw new ElevateSnapshotChangedException();
        }
    }

    private void requireResource(String table, Object resourceId, String resourceType) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE resource_id = ?", Long.class, resourceId);
        if (count == null || count == 0) {
            throw new ElevateResourceNotFoundException(resourceType);
        }
    }

    private static SearchClause productSearch(String query) {
        if (query.isBlank()) {
            return SearchClause.none();
        }
        String sql = "(LOWER(name) LIKE ? ESCAPE '!' OR LOWER(slug) LIKE ? ESCAPE '!'"
                + " OR LOWER(resource_id) LIKE ? ESCAPE '!' OR LOWER(COALESCE(customer, '')) LIKE ? ESCAPE '!')";
        return SearchClause.repeated(sql, searchPattern(query), 4);
    }

    private static SearchClause journeySearch(String query) {
        if (query.isBlank()) {
            return SearchClause.none();
        }
        String sql = "(LOWER(name) LIKE ? ESCAPE '!' OR LOWER(slug) LIKE ? ESCAPE '!'"
                + " OR LOWER(resource_id) LIKE ? ESCAPE '!')";
        return SearchClause.repeated(sql, searchPattern(query), 3);
    }

    private static SearchClause userSearch(String query) {
        if (query.isBlank()) {
            return SearchClause.none();
        }
        String sql = "(LOWER(name) LIKE ? ESCAPE '!' OR LOWER(resource_id::TEXT) LIKE ? ESCAPE '!')";
        return SearchClause.repeated(sql, searchPattern(query), 2);
    }

    private static String searchPattern(String query) {
        String escaped = query.trim()
                .toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }

    private <T> List<T> readResources(String table, Class<T> type) {
        String sql = "SELECT payload::text FROM " + table + " ORDER BY resource_id";
        return jdbcTemplate.query(sql, (resultSet, rowNumber) -> fromJson(resultSet.getString(1), type));
    }

    private void insertProducts(List<ElevateProduct> products, Map<String, JsonNode> payloads) {
        if (products.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO elevate_products
                       (resource_id, slug, name, customer, created_at, last_updated_at, payload)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS JSONB))
                """;
        jdbcTemplate.batchUpdate(sql, products, INSERT_BATCH_SIZE, (statement, product) -> {
            statement.setString(1, product.id());
            statement.setString(2, product.slug());
            statement.setString(3, product.name());
            statement.setString(4, product.customer());
            statement.setObject(5, product.createdAt());
            statement.setObject(6, product.lastUpdatedAt());
            statement.setString(7, toJson(payloads.getOrDefault(product.id(), objectMapper.valueToTree(product))));
        });
    }

    private void insertUsers(List<ElevateUser> users, Map<UUID, JsonNode> payloads) {
        if (users.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO elevate_users
                       (resource_id, product_id, name, description, created_at, last_updated_at, payload)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS JSONB))
                """;
        jdbcTemplate.batchUpdate(sql, users, INSERT_BATCH_SIZE, (statement, user) -> {
            statement.setObject(1, user.id());
            statement.setString(2, user.productId());
            statement.setString(3, user.name());
            statement.setString(4, user.description());
            statement.setObject(5, user.createdAt());
            statement.setObject(6, user.lastUpdatedAt());
            statement.setString(7, toJson(payloads.getOrDefault(user.id(), objectMapper.valueToTree(user))));
        });
    }

    private void insertJourneys(List<ElevateJourney> journeys, Map<String, JsonNode> payloads) {
        if (journeys.isEmpty()) {
            return;
        }
        String sql = """
                INSERT INTO elevate_journeys
                       (resource_id, slug, name, product_id, product_slug, user_description, primary_problems,
                        created_at, last_updated_at, payload)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB))
                """;
        jdbcTemplate.batchUpdate(sql, journeys, INSERT_BATCH_SIZE, (statement, journey) -> {
            statement.setString(1, journey.id());
            statement.setString(2, journey.slug());
            statement.setString(3, journey.name());
            statement.setString(4, journey.productId());
            statement.setString(5, journey.productSlug());
            statement.setString(6, journey.userDescription());
            statement.setString(7, journey.primaryProblems());
            statement.setObject(8, journey.createdAt());
            statement.setObject(9, journey.lastUpdatedAt());
            statement.setString(10, toJson(payloads.getOrDefault(journey.id(), objectMapper.valueToTree(journey))));
        });
    }

    private void insertJourneyUsers(List<ElevateJourney> journeys) {
        List<JourneyUser> batch = new ArrayList<>(INSERT_BATCH_SIZE);
        for (ElevateJourney journey : journeys) {
            for (UUID userId : new LinkedHashSet<>(journey.userIds())) {
                batch.add(new JourneyUser(journey.id(), userId));
                if (batch.size() == INSERT_BATCH_SIZE) {
                    insertJourneyUserBatch(batch);
                    batch = new ArrayList<>(INSERT_BATCH_SIZE);
                }
            }
        }
        if (!batch.isEmpty()) {
            insertJourneyUserBatch(batch);
        }
    }

    private void insertJourneyUserBatch(List<JourneyUser> relationships) {
        jdbcTemplate.batchUpdate(
                "INSERT INTO elevate_journey_users (journey_id, user_id) VALUES (?, ?)",
                relationships,
                INSERT_BATCH_SIZE,
                (statement, relationship) -> {
                    statement.setString(1, relationship.journeyId());
                    statement.setObject(2, relationship.userId());
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

    private static LocalDateTime localDateTime(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getTimestamp(column).toLocalDateTime();
    }

    private static final RowMapper<ElevateProductSummary> PRODUCT_MAPPER =
            (resultSet, rowNumber) -> new ElevateProductSummary(
                    resultSet.getString("resource_id"),
                    resultSet.getString("slug"),
                    resultSet.getString("name"),
                    resultSet.getString("customer"),
                    localDateTime(resultSet, "created_at"),
                    localDateTime(resultSet, "last_updated_at"),
                    resultSet.getLong("journey_count"),
                    resultSet.getLong("user_count"),
                    resultSet.getLong("assignment_count"));

    private static final RowMapper<ElevateJourneySummary> JOURNEY_MAPPER =
            (resultSet, rowNumber) -> new ElevateJourneySummary(
                    resultSet.getString("resource_id"),
                    resultSet.getString("slug"),
                    resultSet.getString("name"),
                    resultSet.getString("product_id"),
                    resultSet.getString("product_slug"),
                    resultSet.getString("user_description"),
                    resultSet.getString("primary_problems"),
                    localDateTime(resultSet, "created_at"),
                    localDateTime(resultSet, "last_updated_at"),
                    resultSet.getLong("user_count"),
                    resultSet.getLong("missing_user_count"),
                    resultSet.getLong("cross_product_user_count"));

    private static final RowMapper<ElevateUserSummary> USER_MAPPER = (resultSet, rowNumber) -> new ElevateUserSummary(
            resultSet.getObject("resource_id", UUID.class),
            resultSet.getString("product_id"),
            resultSet.getString("name"),
            resultSet.getString("description"),
            localDateTime(resultSet, "created_at"),
            localDateTime(resultSet, "last_updated_at"),
            resultSet.getLong("journey_count"));

    private static final RowMapper<ElevateIntegrityItem> INTEGRITY_MAPPER =
            (resultSet, rowNumber) -> new ElevateIntegrityItem(
                    ElevateIntegrityItem.Type.valueOf(resultSet.getString("type")),
                    resultSet.getString("journey_id"),
                    resultSet.getString("journey_name"),
                    resultSet.getString("journey_product_id"),
                    resultSet.getObject("user_id", UUID.class),
                    resultSet.getString("user_name"),
                    resultSet.getString("user_product_id"));

    private record JourneyUser(String journeyId, UUID userId) {}

    private record SearchClause(String sql, List<Object> parameters) {
        private static SearchClause none() {
            return new SearchClause("TRUE", List.of());
        }

        private static SearchClause repeated(String sql, String pattern, int count) {
            List<Object> parameters = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                parameters.add(pattern);
            }
            return new SearchClause(sql, List.copyOf(parameters));
        }
    }
}
