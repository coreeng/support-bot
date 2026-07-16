package com.coreeng.supportbot.elevate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIf("localDatabaseEnabled")
class ElevateV35MigrationTest {
    private static final UUID STORED_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MISSING_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    static boolean localDatabaseEnabled() {
        return Boolean.getBoolean("docker") || "true".equals(System.getenv("SUPPORTBOT_USE_LOCAL_DB"));
    }

    @Test
    void migratesPopulatedV34SnapshotWithoutLosingPayloadOrRelationshipEvidence() {
        String schema = "elevate_v35_test_" + UUID.randomUUID().toString().replace("-", "");
        DataSource dataSource = dataSource();
        JdbcTemplate database = new JdbcTemplate(dataSource);
        database.execute("CREATE SCHEMA " + schema);
        try {
            migrate(dataSource, schema, "34");
            seedV34Snapshot(database, schema);

            migrate(dataSource, schema, "35");

            assertNormalizedBackfill(database, schema);
            assertRawPayloadRetained(database, schema);
            assertRelationshipsPreserved(database, schema);
            assertIntegrityBackfill(database, schema);
            assertThat(database.queryForObject(
                            "SELECT snapshot_version FROM " + schema + ".elevate_sync_state WHERE singleton = TRUE",
                            UUID.class))
                    .isNotNull();
            assertThat(database.queryForObject("""
                            SELECT namespace.nspname
                              FROM pg_extension extension
                              JOIN pg_namespace namespace ON namespace.oid = extension.extnamespace
                             WHERE extension.extname = 'pg_trgm'
                            """, String.class)).isEqualTo("public");
            assertTrigramSearchIndexes(database, schema);
            assertIntegritySearchUsesTrigramIndex(database, schema);
        } finally {
            database.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    private static void migrate(DataSource dataSource, String schema, String target) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .defaultSchema(schema)
                .schemas(schema)
                .target(target)
                .load()
                .migrate();
    }

    private static void seedV34Snapshot(JdbcTemplate database, String schema) {
        database.update(
                "INSERT INTO " + schema + ".elevate_products (resource_id, payload) VALUES (?, CAST(? AS JSONB))",
                "product-1",
                """
                {
                  "id": "product-1",
                  "slug": "product-one",
                  "name": "Product One",
                  "customer": "Customer One",
                  "createdAt": "2026-01-02T03:04:05",
                  "lastUpdatedAt": "2026-02-03T04:05:06",
                  "futureField": {"retained": true}
                }
                """);
        database.update(
                "INSERT INTO " + schema + ".elevate_users (resource_id, payload) VALUES (?, CAST(? AS JSONB))",
                STORED_USER_ID,
                """
                {
                  "id": "11111111-1111-1111-1111-111111111111",
                  "productId": "product-1",
                  "name": "Stored User",
                  "description": "Description",
                  "createdAt": "2026-01-02T03:04:05",
                  "lastUpdatedAt": "2026-02-03T04:05:06"
                }
                """);
        database.update(
                "INSERT INTO " + schema + ".elevate_journeys (resource_id, payload) VALUES (?, CAST(? AS JSONB))",
                "journey-1",
                """
                {
                  "id": "journey-1",
                  "slug": "journey-one",
                  "name": "Journey One",
                  "productId": "product-1",
                  "productSlug": "product-one",
                  "userDescription": "Users",
                  "primaryProblems": "Problems",
                  "userIds": [
                    "11111111-1111-1111-1111-111111111111",
                    "11111111-1111-1111-1111-111111111111",
                    "22222222-2222-2222-2222-222222222222"
                  ],
                  "createdAt": "2026-01-02T03:04:05",
                  "lastUpdatedAt": "2026-02-03T04:05:06",
                  "futureJourneyField": "retained"
                }
                """);
        database.update("""
                UPDATE %s.elevate_sync_state
                   SET last_sync_attempt_at = TIMESTAMPTZ '2026-07-13T10:00:00Z',
                       last_sync_success_at = TIMESTAMPTZ '2026-07-13T10:00:05Z',
                       last_sync_succeeded = TRUE
                 WHERE singleton = TRUE
                """.formatted(schema));
    }

    private static void assertNormalizedBackfill(JdbcTemplate database, String schema) {
        Map<String, Object> product = database.queryForMap("""
                SELECT slug, name, customer
                  FROM %s.elevate_products
                 WHERE resource_id = 'product-1'
                """.formatted(schema));
        assertThat(product)
                .containsEntry("slug", "product-one")
                .containsEntry("name", "Product One")
                .containsEntry("customer", "Customer One");

        Map<String, Object> user = database.queryForMap("""
                SELECT product_id, name, description
                  FROM %s.elevate_users
                 WHERE resource_id = '11111111-1111-1111-1111-111111111111'
                """.formatted(schema));
        assertThat(user)
                .containsEntry("product_id", "product-1")
                .containsEntry("name", "Stored User")
                .containsEntry("description", "Description");

        Map<String, Object> journey = database.queryForMap("""
                SELECT slug, name, product_id, product_slug, user_description, primary_problems
                  FROM %s.elevate_journeys
                 WHERE resource_id = 'journey-1'
                """.formatted(schema));
        assertThat(journey)
                .containsEntry("slug", "journey-one")
                .containsEntry("name", "Journey One")
                .containsEntry("product_id", "product-1")
                .containsEntry("product_slug", "product-one")
                .containsEntry("user_description", "Users")
                .containsEntry("primary_problems", "Problems");
    }

    private static void assertRawPayloadRetained(JdbcTemplate database, String schema) {
        assertThat(database.queryForObject(
                        "SELECT payload #>> '{futureField,retained}' FROM " + schema
                                + ".elevate_products WHERE resource_id = 'product-1'",
                        String.class))
                .isEqualTo("true");
        assertThat(database.queryForObject(
                        "SELECT payload ->> 'futureJourneyField' FROM " + schema
                                + ".elevate_journeys WHERE resource_id = 'journey-1'",
                        String.class))
                .isEqualTo("retained");
        assertThat(database.queryForObject(
                        "SELECT jsonb_array_length(payload -> 'userIds') FROM " + schema
                                + ".elevate_journeys WHERE resource_id = 'journey-1'",
                        Integer.class))
                .isEqualTo(3);
    }

    private static void assertRelationshipsPreserved(JdbcTemplate database, String schema) {
        assertThat(database.queryForObject("SELECT COUNT(*) FROM " + schema + ".elevate_journey_users", Long.class))
                .isEqualTo(2);
        assertThat(database.queryForObject(
                        "SELECT COUNT(*) FROM " + schema
                                + ".elevate_journey_users WHERE journey_id = 'journey-1' AND user_id = ?",
                        Long.class,
                        STORED_USER_ID))
                .isEqualTo(1);
        assertThat(database.queryForObject(
                        "SELECT COUNT(*) FROM " + schema
                                + ".elevate_journey_users WHERE journey_id = 'journey-1' AND user_id = ?",
                        Long.class,
                        MISSING_USER_ID))
                .isEqualTo(1);
    }

    private static void assertIntegrityBackfill(JdbcTemplate database, String schema) {
        Map<String, Object> item = database.queryForMap("""
                SELECT type, journey_id, journey_name, journey_product_id, user_id, search_text
                  FROM %s.elevate_integrity_items
                """.formatted(schema));
        assertThat(item)
                .containsEntry("type", "MISSING_ASSIGNMENT")
                .containsEntry("journey_id", "journey-1")
                .containsEntry("journey_name", "Journey One")
                .containsEntry("journey_product_id", "product-1")
                .containsEntry("user_id", MISSING_USER_ID);
        assertThat(Objects.requireNonNull(item.get("search_text")).toString())
                .contains("journey one", "journey-1", MISSING_USER_ID.toString());
    }

    private static void assertTrigramSearchIndexes(JdbcTemplate database, String schema) {
        List<String> definitions = database.queryForList("""
                SELECT indexdef
                  FROM pg_indexes
                 WHERE schemaname = ?
                   AND indexname IN (
                       'elevate_products_name_trgm_idx',
                       'elevate_products_slug_trgm_idx',
                       'elevate_products_resource_id_trgm_idx',
                       'elevate_products_customer_trgm_idx',
                       'elevate_journeys_name_trgm_idx',
                       'elevate_journeys_slug_trgm_idx',
                       'elevate_journeys_resource_id_trgm_idx',
                       'elevate_users_name_trgm_idx',
                       'elevate_users_resource_id_trgm_idx',
                       'elevate_integrity_items_search_trgm_idx'
                   )
                """, String.class, schema);
        assertThat(definitions).hasSize(10).allSatisfy(definition -> assertThat(definition)
                .contains("USING gin", "gin_trgm_ops"));
    }

    private static void assertIntegritySearchUsesTrigramIndex(JdbcTemplate database, String schema) {
        List<String> plan = Objects.requireNonNull(database.execute((ConnectionCallback<List<String>>) connection -> {
            List<String> lines = new ArrayList<>();
            try (var statement = connection.createStatement()) {
                statement.execute("SET enable_seqscan = off");
                try (var resultSet = statement.executeQuery("""
                        EXPLAIN (COSTS OFF)
                        SELECT *
                          FROM %s.elevate_integrity_items
                         WHERE search_text LIKE '%%22222222%%' ESCAPE '!'
                        """.formatted(schema))) {
                    while (resultSet.next()) {
                        lines.add(resultSet.getString(1));
                    }
                } finally {
                    statement.execute("RESET enable_seqscan");
                }
            }
            return lines;
        }));
        assertThat(plan).anySatisfy(line -> assertThat(line).contains("elevate_integrity_items_search_trgm_idx"));
    }

    private static DataSource dataSource() {
        String url =
                value("supportbot.localDb.url", "SUPPORTBOT_LOCAL_DB_URL", "jdbc:postgresql://localhost:5432/postgres");
        String username = value("supportbot.localDb.user", "SUPPORTBOT_LOCAL_DB_USER", "postgres");
        String password = value("supportbot.localDb.password", "SUPPORTBOT_LOCAL_DB_PASSWORD", "postgres");
        return new DriverManagerDataSource(url, username, password);
    }

    private static String value(String systemProperty, String environmentVariable, String defaultValue) {
        String systemValue = System.getProperty(systemProperty);
        if (systemValue != null) {
            return systemValue;
        }
        String environmentValue = System.getenv(environmentVariable);
        return environmentValue == null ? defaultValue : environmentValue;
    }
}
