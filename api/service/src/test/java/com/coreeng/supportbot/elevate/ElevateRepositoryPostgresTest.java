package com.coreeng.supportbot.elevate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coreeng.supportbot.util.JsonMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringJUnitConfig(ElevateRepositoryPostgresTest.TestConfig.class)
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@EnabledIf("localDatabaseEnabled")
class ElevateRepositoryPostgresTest {
    private static final Instant ATTEMPTED_AT = Instant.parse("2026-07-13T10:00:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-07-13T10:00:05Z");

    private final ElevateRepository repository;
    private final JdbcTemplate jdbcTemplate;

    ElevateRepositoryPostgresTest(ElevateRepository repository, JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    static boolean localDatabaseEnabled() {
        return Boolean.getBoolean("docker") || "true".equals(System.getenv("SUPPORTBOT_USE_LOCAL_DB"));
    }

    @BeforeEach
    void clearSnapshot() {
        jdbcTemplate.update("DELETE FROM elevate_integrity_items");
        jdbcTemplate.update("DELETE FROM elevate_journey_users");
        jdbcTemplate.update("DELETE FROM elevate_journeys");
        jdbcTemplate.update("DELETE FROM elevate_users");
        jdbcTemplate.update("DELETE FROM elevate_products");
        jdbcTemplate.update("""
                UPDATE elevate_sync_state
                   SET last_ping_attempt_at = NULL,
                       last_ping_success_at = NULL,
                       last_ping_succeeded = NULL,
                       last_ping_error = NULL,
                       last_sync_attempt_at = NULL,
                       last_sync_success_at = NULL,
                       last_sync_succeeded = NULL,
                       last_sync_error = NULL,
                       snapshot_version = NULL
                 WHERE singleton = TRUE
                """);
    }

    @Test
    void replacesExistingSnapshotIncludingWithAuthoritativeEmptyCollections() {
        repository.replaceSnapshot(snapshot(product("old")), ATTEMPTED_AT, COMPLETED_AT);
        UUID populatedVersion = repository.getStoredStatus().snapshotVersion();

        repository.replaceSnapshot(new ElevateSnapshot(List.of(), List.of(), List.of()), ATTEMPTED_AT, COMPLETED_AT);

        ElevateStoredStatus storedStatus = repository.getStoredStatus();
        assertThat(repository.getSnapshot()).isEqualTo(new ElevateSnapshot(List.of(), List.of(), List.of()));
        assertThat(storedStatus.counts()).isEqualTo(new ElevateCounts(0, 0, 0, 0));
        assertThat(storedStatus.snapshotVersion()).isNotNull().isNotEqualTo(populatedVersion);
        ElevateSyncState state = storedStatus.state();
        assertThat(state.lastSyncSucceeded()).isTrue();
        assertThat(state.lastSyncAttemptAt()).isEqualTo(ATTEMPTED_AT);
        assertThat(state.lastSyncSuccessAt()).isEqualTo(COMPLETED_AT);
    }

    @Test
    void rollsBackWholeReplacementAndRetainsLastGoodSnapshotOnInsertFailure() {
        ElevateProduct oldProduct = product("old");
        ElevateJourney orphanJourney = journey("orphan", "missing-product", List.of());
        repository.replaceSnapshot(
                new ElevateSnapshot(List.of(oldProduct), List.of(), List.of(orphanJourney)),
                ATTEMPTED_AT,
                COMPLETED_AT);
        UUID lastGoodVersion =
                Objects.requireNonNull(repository.getStoredStatus().snapshotVersion());
        ElevateProduct duplicate = product("duplicate");
        ElevateSnapshot invalidSnapshot = new ElevateSnapshot(List.of(duplicate, duplicate), List.of(), List.of());

        assertThatThrownBy(() -> repository.replaceSnapshot(invalidSnapshot, ATTEMPTED_AT, COMPLETED_AT))
                .isInstanceOf(RuntimeException.class);

        assertThat(repository.getSnapshot().products()).containsExactly(oldProduct);
        assertThat(repository.getStoredStatus().snapshotVersion()).isEqualTo(lastGoodVersion);
        assertThat(repository
                        .findIntegrity(lastGoodVersion, ElevateIntegrityType.ALL, defaultQuery())
                        .content())
                .extracting(ElevateIntegrityItem::type)
                .containsExactly(ElevateIntegrityItem.Type.ORPHAN_JOURNEY);
    }

    @Test
    void storesNormalizedRelationshipsWithoutDiscardingRawPayloadOrIntegrityEvidence() {
        UUID linkedUserId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID crossProductUserId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID missingUserId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        ElevateProduct product = product("product-1");
        ElevateUser linkedUser = user(linkedUserId, "product-1", "Linked user");
        ElevateUser crossProductUser = user(crossProductUserId, "missing-product", "Cross-product user");
        ElevateJourney journey = journey(
                "journey-1", "product-1", List.of(linkedUserId, linkedUserId, crossProductUserId, missingUserId));
        ElevateJourney orphanJourney = journey("orphan-journey", "orphan-product", List.of());

        ObjectNode rawJourney = new JsonMapper().getObjectMapper().valueToTree(journey);
        rawJourney.put("futureField", "retained");
        repository.replaceSnapshot(
                new ElevateSnapshot(
                        List.of(product),
                        List.of(linkedUser, crossProductUser),
                        List.of(journey, orphanJourney),
                        Map.of(),
                        Map.of(),
                        Map.of(journey.id(), rawJourney)),
                ATTEMPTED_AT,
                COMPLETED_AT);

        ElevateStoredStatus status = repository.getStoredStatus();
        assertThat(status.counts()).isEqualTo(new ElevateCounts(1, 2, 2, 3));
        assertThat(status.integrity()).isEqualTo(new ElevateIntegrityCounts(1, 1, 1, 1));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT jsonb_array_length(payload -> 'userIds') FROM elevate_journeys WHERE resource_id = ?",
                        Integer.class,
                        journey.id()))
                .isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT payload ->> 'futureField' FROM elevate_journeys WHERE resource_id = ?",
                        String.class,
                        journey.id()))
                .isEqualTo("retained");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT name FROM elevate_products WHERE resource_id = ?", String.class, product.id()))
                .isEqualTo(product.name());

        UUID version = status.snapshotVersion();
        assertThat(version).isNotNull();
        ElevateJourneySummary journeySummary =
                repository.findJourney(version, journey.id()).orElseThrow();
        assertThat(journeySummary.userCount()).isEqualTo(1);
        assertThat(journeySummary.missingUserCount()).isEqualTo(1);
        assertThat(journeySummary.crossProductUserCount()).isEqualTo(1);
        assertThat(repository
                        .findJourneyUsers(version, journey.id(), defaultQuery())
                        .content())
                .extracting(ElevateUserSummary::id)
                .containsExactly(linkedUserId);
        assertThat(repository
                        .findIntegrity(version, ElevateIntegrityType.ALL, defaultQuery())
                        .content())
                .extracting(ElevateIntegrityItem::type)
                .containsExactlyInAnyOrder(
                        ElevateIntegrityItem.Type.CROSS_PRODUCT_ASSIGNMENT,
                        ElevateIntegrityItem.Type.MISSING_ASSIGNMENT,
                        ElevateIntegrityItem.Type.ORPHAN_JOURNEY,
                        ElevateIntegrityItem.Type.ORPHAN_USER);
        assertThat(repository
                        .findIntegrity(
                                version,
                                ElevateIntegrityType.ALL,
                                new ElevateReadQuery(
                                        0,
                                        20,
                                        "33333333",
                                        ElevateRelationshipFilter.ALL,
                                        ElevateSort.NAME,
                                        ElevateDirection.ASC))
                        .content())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.type()).isEqualTo(ElevateIntegrityItem.Type.MISSING_ASSIGNMENT);
                    assertThat(item.userId()).isEqualTo(missingUserId);
                });
    }

    @Test
    void pagesSearchesFiltersAndSortsUsingDeterministicNormalizedQueries() {
        ElevateProduct alpha = product("alpha");
        ElevateProduct beta = product("beta");
        ElevateProduct special = product("special!%_");
        ElevateJourney linkedJourney =
                journey("linked", "beta", List.of(UUID.fromString("11111111-1111-1111-1111-111111111111")));
        repository.replaceSnapshot(
                new ElevateSnapshot(List.of(beta, special, alpha), List.of(), List.of(linkedJourney)),
                ATTEMPTED_AT,
                COMPLETED_AT);
        UUID version = repository.getStoredStatus().snapshotVersion();
        assertThat(version).isNotNull();

        var firstPage = repository.findProducts(
                version,
                new ElevateReadQuery(0, 1, "", ElevateRelationshipFilter.ALL, ElevateSort.NAME, ElevateDirection.ASC));
        assertThat(firstPage.content()).extracting(ElevateProductSummary::id).containsExactly("alpha");
        assertThat(firstPage.totalElements()).isEqualTo(3);
        assertThat(firstPage.totalPages()).isEqualTo(3);

        var linked = repository.findProducts(
                version,
                new ElevateReadQuery(
                        0,
                        20,
                        "BETA",
                        ElevateRelationshipFilter.LINKED,
                        ElevateSort.RELATIONSHIPS,
                        ElevateDirection.DESC));
        assertThat(linked.content()).extracting(ElevateProductSummary::id).containsExactly("beta");

        var escapedSearch = repository.findProducts(
                version,
                new ElevateReadQuery(
                        0, 20, "!%_", ElevateRelationshipFilter.ALL, ElevateSort.NAME, ElevateDirection.ASC));
        assertThat(escapedSearch.content())
                .extracting(ElevateProductSummary::id)
                .containsExactly("special!%_");
    }

    @Test
    void excludesCrossProductAssignmentsFromDirectRelationshipsAndLinkedFilters() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        ElevateJourney journey = journey("cross-only", "product-1", List.of(userId));
        repository.replaceSnapshot(
                new ElevateSnapshot(
                        List.of(product("product-1"), product("product-2")),
                        List.of(user(userId, "product-2", "Other product user")),
                        List.of(journey)),
                ATTEMPTED_AT,
                COMPLETED_AT);
        UUID version = repository.getStoredStatus().snapshotVersion();
        assertThat(version).isNotNull();

        assertThat(repository.findJourney(version, journey.id()).orElseThrow().userCount())
                .isZero();
        assertThat(repository
                        .findJourneyUsers(version, journey.id(), defaultQuery())
                        .content())
                .isEmpty();
        assertThat(repository.findUserJourneys(version, userId, defaultQuery()).content())
                .isEmpty();
        assertThat(repository
                        .findProductJourneys(
                                version,
                                "product-1",
                                new ElevateReadQuery(
                                        0,
                                        20,
                                        "",
                                        ElevateRelationshipFilter.UNASSIGNED,
                                        ElevateSort.NAME,
                                        ElevateDirection.ASC))
                        .content())
                .extracting(ElevateJourneySummary::id)
                .containsExactly("cross-only");
    }

    @Test
    void nestedReadsDistinguishAMissingParentFromAnEmptyRelationshipPage() {
        repository.replaceSnapshot(snapshot(product("product-1")), ATTEMPTED_AT, COMPLETED_AT);
        UUID version = repository.getStoredStatus().snapshotVersion();
        assertThat(version).isNotNull();

        assertThat(repository
                        .findProductJourneys(version, "product-1", defaultQuery())
                        .content())
                .isEmpty();
        assertThatThrownBy(() -> repository.findProductJourneys(version, "missing", defaultQuery()))
                .isInstanceOf(ElevateResourceNotFoundException.class)
                .hasMessage("Elevate product not found");
    }

    @Test
    void rejectsAReadAfterSnapshotRollover() {
        repository.replaceSnapshot(snapshot(product("first")), ATTEMPTED_AT, COMPLETED_AT);
        UUID firstVersion = repository.getStoredStatus().snapshotVersion();
        assertThat(firstVersion).isNotNull();
        repository.replaceSnapshot(snapshot(product("second")), ATTEMPTED_AT, COMPLETED_AT);

        assertThatThrownBy(() -> repository.findProducts(firstVersion, defaultQuery()))
                .isInstanceOf(ElevateSnapshotChangedException.class);
    }

    private static ElevateSnapshot snapshot(ElevateProduct product) {
        return new ElevateSnapshot(List.of(product), List.of(), List.of());
    }

    private static ElevateProduct product(String id) {
        return new ElevateProduct(
                id,
                id + "-slug",
                id + " name",
                null,
                LocalDateTime.parse("2026-01-02T03:04:05"),
                LocalDateTime.parse("2026-02-03T04:05:06"));
    }

    private static ElevateUser user(UUID id, String productId, String name) {
        return new ElevateUser(
                id,
                productId,
                name,
                null,
                LocalDateTime.parse("2026-01-02T03:04:05"),
                LocalDateTime.parse("2026-02-03T04:05:06"));
    }

    private static ElevateJourney journey(String id, String productId, List<UUID> userIds) {
        return new ElevateJourney(
                id,
                id + "-slug",
                id + " name",
                productId,
                productId + "-slug",
                null,
                null,
                userIds,
                LocalDateTime.parse("2026-01-02T03:04:05"),
                LocalDateTime.parse("2026-02-03T04:05:06"));
    }

    private static ElevateReadQuery defaultQuery() {
        return new ElevateReadQuery(0, 20, "", ElevateRelationshipFilter.ALL, ElevateSort.NAME, ElevateDirection.ASC);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    static class TestConfig {
        @Bean
        DataSource dataSource() {
            String url = System.getProperty("supportbot.localDb.url", "jdbc:postgresql://localhost:5432/postgres");
            String username = System.getProperty("supportbot.localDb.user", "postgres");
            String password = System.getProperty("supportbot.localDb.password", "postgres");
            return new DriverManagerDataSource(url, username, password);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new JsonMapper().getObjectMapper();
        }

        @Bean
        ElevateRepository elevateRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
            return new ElevateRepository(jdbcTemplate, objectMapper);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }
}
