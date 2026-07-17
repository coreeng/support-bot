package com.coreeng.supportbot.elevate;

import static com.coreeng.supportbot.dbschema.Tables.ELEVATE_INTEGRITY_ITEMS;
import static com.coreeng.supportbot.dbschema.Tables.ELEVATE_INTEGRITY_ITEM_SOURCE;
import static com.coreeng.supportbot.dbschema.Tables.ELEVATE_JOURNEYS;
import static com.coreeng.supportbot.dbschema.Tables.ELEVATE_JOURNEY_USERS;
import static com.coreeng.supportbot.dbschema.Tables.ELEVATE_PRODUCTS;
import static com.coreeng.supportbot.dbschema.Tables.ELEVATE_SYNC_STATE;
import static com.coreeng.supportbot.dbschema.Tables.ELEVATE_USERS;
import static org.jooq.impl.DSL.falseCondition;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.lower;
import static org.jooq.impl.DSL.noCondition;
import static org.jooq.impl.DSL.selectCount;

import com.coreeng.supportbot.dbschema.tables.ElevateJourneys;
import com.coreeng.supportbot.dbschema.tables.ElevateProducts;
import com.coreeng.supportbot.dbschema.tables.ElevateUsers;
import com.coreeng.supportbot.util.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Query;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Select;
import org.jooq.SortField;
import org.jooq.Table;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ElevateRepository {
    private static final int INSERT_BATCH_SIZE = 500;

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ElevateStoredStatus getStoredStatus() {
        @Nullable UUID snapshotVersion = dsl.select(ELEVATE_SYNC_STATE.SNAPSHOT_VERSION)
                .from(ELEVATE_SYNC_STATE)
                .where(ELEVATE_SYNC_STATE.SINGLETON.isTrue())
                .fetchOne(ELEVATE_SYNC_STATE.SNAPSHOT_VERSION);
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
        ElevateProducts product = ELEVATE_PRODUCTS.as("product");
        ProductCounts counts = productCounts(product);
        Field<Long> relationships = counts.journeys().add(counts.users());
        Condition condition = productSearch(product, query).and(relationshipFilter(relationships, query));
        long total = count(product, condition);
        List<ElevateProductSummary> content = dsl.select(
                        product.RESOURCE_ID,
                        product.SLUG,
                        product.NAME,
                        product.CUSTOMER,
                        product.CREATED_AT,
                        product.LAST_UPDATED_AT,
                        counts.journeys(),
                        counts.users(),
                        counts.assignments())
                .from(product)
                .where(condition)
                .orderBy(summaryOrder(product.NAME, product.RESOURCE_ID, relationships, query))
                .limit(query.pageSize())
                .offset(offset(query))
                .fetch(record -> productSummary(record, product, counts));
        return page(content, total, query);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<ElevateProductSummary> findProduct(UUID snapshotVersion, String productId) {
        requireSnapshotVersion(snapshotVersion);
        ElevateProducts product = ELEVATE_PRODUCTS.as("product");
        ProductCounts counts = productCounts(product);
        return dsl.select(
                        product.RESOURCE_ID,
                        product.SLUG,
                        product.NAME,
                        product.CUSTOMER,
                        product.CREATED_AT,
                        product.LAST_UPDATED_AT,
                        counts.journeys(),
                        counts.users(),
                        counts.assignments())
                .from(product)
                .where(product.RESOURCE_ID.eq(productId))
                .fetchOptional(record -> productSummary(record, product, counts));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<ElevateJourneySummary> findProductJourneys(
            UUID snapshotVersion, String productId, ElevateReadQuery query) {
        requireSnapshotVersion(snapshotVersion);
        requireProduct(productId);
        ElevateJourneys journey = ELEVATE_JOURNEYS.as("journey");
        JourneyCounts counts = journeyCounts(journey);
        Condition condition = journey.PRODUCT_ID
                .eq(productId)
                .and(journeySearch(journey, query))
                .and(relationshipFilter(counts.users(), query));
        long total = count(journey, condition);
        List<ElevateJourneySummary> content = journeySummaries(journey, counts, condition, query);
        return page(content, total, query);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<ElevateUserSummary> findProductUsers(UUID snapshotVersion, String productId, ElevateReadQuery query) {
        requireSnapshotVersion(snapshotVersion);
        requireProduct(productId);
        ElevateUsers user = ELEVATE_USERS.as("product_user");
        Field<Long> journeyCount = userJourneyCount(user);
        Condition condition =
                user.PRODUCT_ID.eq(productId).and(userSearch(user, query)).and(relationshipFilter(journeyCount, query));
        long total = count(user, condition);
        List<ElevateUserSummary> content = userSummaries(user, journeyCount, condition, query);
        return page(content, total, query);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<ElevateJourneySummary> findJourney(UUID snapshotVersion, String journeyId) {
        requireSnapshotVersion(snapshotVersion);
        ElevateJourneys journey = ELEVATE_JOURNEYS.as("journey");
        JourneyCounts counts = journeyCounts(journey);
        return dsl.select(
                        journey.RESOURCE_ID,
                        journey.SLUG,
                        journey.NAME,
                        journey.PRODUCT_ID,
                        journey.PRODUCT_SLUG,
                        journey.USER_DESCRIPTION,
                        journey.PRIMARY_PROBLEMS,
                        journey.CREATED_AT,
                        journey.LAST_UPDATED_AT,
                        counts.users(),
                        counts.missingUsers(),
                        counts.crossProductUsers())
                .from(journey)
                .where(journey.RESOURCE_ID.eq(journeyId))
                .fetchOptional(record -> journeySummary(record, journey, counts));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<ElevateUserSummary> findJourneyUsers(UUID snapshotVersion, String journeyId, ElevateReadQuery query) {
        requireSnapshotVersion(snapshotVersion);
        requireJourney(journeyId);
        ElevateUsers user = ELEVATE_USERS.as("journey_user");
        var relation = ELEVATE_JOURNEY_USERS.as("journey_relation");
        ElevateJourneys parent = ELEVATE_JOURNEYS.as("parent_journey");
        Field<Long> journeyCount = userJourneyCount(user);
        Condition condition = relation.JOURNEY_ID
                .eq(journeyId)
                .and(user.PRODUCT_ID.eq(parent.PRODUCT_ID))
                .and(userSearch(user, query))
                .and(relationshipFilter(journeyCount, query));
        Table<?> source = user.join(relation)
                .on(relation.USER_ID.eq(user.RESOURCE_ID))
                .join(parent)
                .on(parent.RESOURCE_ID.eq(relation.JOURNEY_ID));
        long total = count(source, condition);
        List<ElevateUserSummary> content = userSummaries(user, journeyCount, source, condition, query);
        return page(content, total, query);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<ElevateUserSummary> findUser(UUID snapshotVersion, UUID userId) {
        requireSnapshotVersion(snapshotVersion);
        ElevateUsers user = ELEVATE_USERS.as("product_user");
        Field<Long> journeyCount = userJourneyCount(user);
        return dsl.select(
                        user.RESOURCE_ID,
                        user.PRODUCT_ID,
                        user.NAME,
                        user.DESCRIPTION,
                        user.CREATED_AT,
                        user.LAST_UPDATED_AT,
                        journeyCount)
                .from(user)
                .where(user.RESOURCE_ID.eq(userId))
                .fetchOptional(record -> userSummary(record, user, journeyCount));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<ElevateJourneySummary> findUserJourneys(UUID snapshotVersion, UUID userId, ElevateReadQuery query) {
        requireSnapshotVersion(snapshotVersion);
        requireUser(userId);
        ElevateJourneys journey = ELEVATE_JOURNEYS.as("user_journey");
        var relation = ELEVATE_JOURNEY_USERS.as("user_relation");
        ElevateUsers parent = ELEVATE_USERS.as("parent_user");
        JourneyCounts counts = journeyCounts(journey);
        Condition condition = relation.USER_ID
                .eq(userId)
                .and(journey.PRODUCT_ID.eq(parent.PRODUCT_ID))
                .and(journeySearch(journey, query))
                .and(relationshipFilter(counts.users(), query));
        Table<?> source = journey.join(relation)
                .on(relation.JOURNEY_ID.eq(journey.RESOURCE_ID))
                .join(parent)
                .on(parent.RESOURCE_ID.eq(relation.USER_ID));
        long total = count(source, condition);
        List<ElevateJourneySummary> content = journeySummaries(journey, counts, source, condition, query);
        return page(content, total, query);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<ElevateIntegrityItem> findIntegrity(
            UUID snapshotVersion, ElevateIntegrityType type, ElevateReadQuery query) {
        requireSnapshotVersion(snapshotVersion);
        var integrity = ELEVATE_INTEGRITY_ITEMS.as("integrity");
        Condition condition = noCondition();
        if (type != ElevateIntegrityType.ALL) {
            condition = condition.and(integrity.TYPE.eq(type.databaseValue()));
        }
        if (!query.query().isBlank()) {
            condition = condition.and(integrity.SEARCH_TEXT.containsIgnoreCase(query.query()));
        }
        long total = count(integrity, condition);
        List<SortField<?>> order = query.sort() == ElevateSort.RELATIONSHIPS
                ? List.of(
                        ordered(integrity.TYPE, query.direction()), integrity.SORT_NAME.asc(), integrity.SORT_ID.asc())
                : List.of(
                        ordered(integrity.SORT_NAME, query.direction()),
                        ordered(integrity.SORT_ID, query.direction()),
                        integrity.TYPE.asc());
        List<ElevateIntegrityItem> content = dsl.select(
                        integrity.TYPE,
                        integrity.JOURNEY_ID,
                        integrity.JOURNEY_NAME,
                        integrity.JOURNEY_PRODUCT_ID,
                        integrity.USER_ID,
                        integrity.USER_NAME,
                        integrity.USER_PRODUCT_ID)
                .from(integrity)
                .where(condition)
                .orderBy(order)
                .limit(query.pageSize())
                .offset(offset(query))
                .fetch(record -> new ElevateIntegrityItem(
                        ElevateIntegrityItem.Type.valueOf(record.get(integrity.TYPE)),
                        record.get(integrity.JOURNEY_ID),
                        record.get(integrity.JOURNEY_NAME),
                        record.get(integrity.JOURNEY_PRODUCT_ID),
                        record.get(integrity.USER_ID),
                        record.get(integrity.USER_NAME),
                        record.get(integrity.USER_PRODUCT_ID)));
        return page(content, total, query);
    }

    @Transactional
    public void replaceSnapshot(ElevateSnapshot snapshot, Instant attemptedAt, Instant completedAt) {
        UUID snapshotVersion = UUID.randomUUID();
        dsl.deleteFrom(ELEVATE_INTEGRITY_ITEMS).execute();
        dsl.deleteFrom(ELEVATE_JOURNEY_USERS).execute();
        dsl.deleteFrom(ELEVATE_JOURNEYS).execute();
        dsl.deleteFrom(ELEVATE_USERS).execute();
        dsl.deleteFrom(ELEVATE_PRODUCTS).execute();

        insertProducts(snapshot.products(), snapshot.productPayloads());
        insertUsers(snapshot.users(), snapshot.userPayloads());
        insertJourneys(snapshot.journeys(), snapshot.journeyPayloads());
        insertJourneyUsers(snapshot.journeys());
        refreshIntegrityItems();

        dsl.update(ELEVATE_SYNC_STATE)
                .set(ELEVATE_SYNC_STATE.LAST_SYNC_ATTEMPT_AT, attemptedAt)
                .set(ELEVATE_SYNC_STATE.LAST_SYNC_SUCCESS_AT, completedAt)
                .set(ELEVATE_SYNC_STATE.LAST_SYNC_SUCCEEDED, true)
                .setNull(ELEVATE_SYNC_STATE.LAST_SYNC_ERROR)
                .set(ELEVATE_SYNC_STATE.SNAPSHOT_VERSION, snapshotVersion)
                .where(ELEVATE_SYNC_STATE.SINGLETON.isTrue())
                .execute();
    }

    public void recordSyncAttempt(Instant attemptedAt) {
        dsl.update(ELEVATE_SYNC_STATE)
                .set(ELEVATE_SYNC_STATE.LAST_SYNC_ATTEMPT_AT, attemptedAt)
                .setNull(ELEVATE_SYNC_STATE.LAST_SYNC_SUCCEEDED)
                .setNull(ELEVATE_SYNC_STATE.LAST_SYNC_ERROR)
                .where(ELEVATE_SYNC_STATE.SINGLETON.isTrue())
                .execute();
    }

    public void recordSyncFailure(Instant attemptedAt, String error) {
        dsl.update(ELEVATE_SYNC_STATE)
                .set(ELEVATE_SYNC_STATE.LAST_SYNC_ATTEMPT_AT, attemptedAt)
                .set(ELEVATE_SYNC_STATE.LAST_SYNC_SUCCEEDED, false)
                .set(ELEVATE_SYNC_STATE.LAST_SYNC_ERROR, error)
                .where(ELEVATE_SYNC_STATE.SINGLETON.isTrue())
                .execute();
    }

    public void recordPingSuccess(Instant attemptedAt, Instant completedAt) {
        dsl.update(ELEVATE_SYNC_STATE)
                .set(ELEVATE_SYNC_STATE.LAST_PING_ATTEMPT_AT, attemptedAt)
                .set(ELEVATE_SYNC_STATE.LAST_PING_SUCCESS_AT, completedAt)
                .set(ELEVATE_SYNC_STATE.LAST_PING_SUCCEEDED, true)
                .setNull(ELEVATE_SYNC_STATE.LAST_PING_ERROR)
                .where(ELEVATE_SYNC_STATE.SINGLETON.isTrue())
                .execute();
    }

    public void recordPingFailure(Instant attemptedAt, String error) {
        dsl.update(ELEVATE_SYNC_STATE)
                .set(ELEVATE_SYNC_STATE.LAST_PING_ATTEMPT_AT, attemptedAt)
                .set(ELEVATE_SYNC_STATE.LAST_PING_SUCCEEDED, false)
                .set(ELEVATE_SYNC_STATE.LAST_PING_ERROR, error)
                .where(ELEVATE_SYNC_STATE.SINGLETON.isTrue())
                .execute();
    }

    private ElevateSnapshot readSnapshot() {
        List<ElevateProduct> products = readProducts();
        List<ElevateUser> users = readUsers();
        List<ElevateJourney> journeys = readJourneys();
        return new ElevateSnapshot(products, users, journeys);
    }

    private List<ElevateProduct> readProducts() {
        return dsl.select(ELEVATE_PRODUCTS.PAYLOAD)
                .from(ELEVATE_PRODUCTS)
                .orderBy(ELEVATE_PRODUCTS.RESOURCE_ID)
                .fetch(record -> fromJson(record.get(ELEVATE_PRODUCTS.PAYLOAD).data(), ElevateProduct.class));
    }

    private List<ElevateUser> readUsers() {
        return dsl.select(ELEVATE_USERS.PAYLOAD)
                .from(ELEVATE_USERS)
                .orderBy(ELEVATE_USERS.RESOURCE_ID)
                .fetch(record -> fromJson(record.get(ELEVATE_USERS.PAYLOAD).data(), ElevateUser.class));
    }

    private List<ElevateJourney> readJourneys() {
        return dsl.select(ELEVATE_JOURNEYS.PAYLOAD)
                .from(ELEVATE_JOURNEYS)
                .orderBy(ELEVATE_JOURNEYS.RESOURCE_ID)
                .fetch(record -> fromJson(record.get(ELEVATE_JOURNEYS.PAYLOAD).data(), ElevateJourney.class));
    }

    private ElevateSyncState readState() {
        return dsl.select(
                        ELEVATE_SYNC_STATE.LAST_PING_ATTEMPT_AT,
                        ELEVATE_SYNC_STATE.LAST_PING_SUCCESS_AT,
                        ELEVATE_SYNC_STATE.LAST_PING_SUCCEEDED,
                        ELEVATE_SYNC_STATE.LAST_PING_ERROR,
                        ELEVATE_SYNC_STATE.LAST_SYNC_ATTEMPT_AT,
                        ELEVATE_SYNC_STATE.LAST_SYNC_SUCCESS_AT,
                        ELEVATE_SYNC_STATE.LAST_SYNC_SUCCEEDED,
                        ELEVATE_SYNC_STATE.LAST_SYNC_ERROR)
                .from(ELEVATE_SYNC_STATE)
                .where(ELEVATE_SYNC_STATE.SINGLETON.isTrue())
                .fetchOptional(record -> new ElevateSyncState(
                        record.get(ELEVATE_SYNC_STATE.LAST_PING_ATTEMPT_AT),
                        record.get(ELEVATE_SYNC_STATE.LAST_PING_SUCCESS_AT),
                        record.get(ELEVATE_SYNC_STATE.LAST_PING_SUCCEEDED),
                        record.get(ELEVATE_SYNC_STATE.LAST_PING_ERROR),
                        record.get(ELEVATE_SYNC_STATE.LAST_SYNC_ATTEMPT_AT),
                        record.get(ELEVATE_SYNC_STATE.LAST_SYNC_SUCCESS_AT),
                        record.get(ELEVATE_SYNC_STATE.LAST_SYNC_SUCCEEDED),
                        record.get(ELEVATE_SYNC_STATE.LAST_SYNC_ERROR)))
                .orElseGet(ElevateSyncState::empty);
    }

    private ElevateCounts readCounts() {
        return new ElevateCounts(
                dsl.fetchCount(ELEVATE_PRODUCTS),
                dsl.fetchCount(ELEVATE_JOURNEYS),
                dsl.fetchCount(ELEVATE_USERS),
                dsl.fetchCount(ELEVATE_JOURNEY_USERS));
    }

    private ElevateIntegrityCounts readIntegrityCounts() {
        return new ElevateIntegrityCounts(
                count(ELEVATE_INTEGRITY_ITEMS, ELEVATE_INTEGRITY_ITEMS.TYPE.eq("ORPHAN_USER")),
                count(ELEVATE_INTEGRITY_ITEMS, ELEVATE_INTEGRITY_ITEMS.TYPE.eq("MISSING_ASSIGNMENT")),
                count(ELEVATE_INTEGRITY_ITEMS, ELEVATE_INTEGRITY_ITEMS.TYPE.eq("CROSS_PRODUCT_ASSIGNMENT")));
    }

    private List<ElevateJourneySummary> journeySummaries(
            ElevateJourneys journey, JourneyCounts counts, Condition condition, ElevateReadQuery query) {
        return journeySummaries(journey, counts, journey, condition, query);
    }

    private List<ElevateJourneySummary> journeySummaries(
            ElevateJourneys journey,
            JourneyCounts counts,
            Table<?> source,
            Condition condition,
            ElevateReadQuery query) {
        return dsl.select(
                        journey.RESOURCE_ID,
                        journey.SLUG,
                        journey.NAME,
                        journey.PRODUCT_ID,
                        journey.PRODUCT_SLUG,
                        journey.USER_DESCRIPTION,
                        journey.PRIMARY_PROBLEMS,
                        journey.CREATED_AT,
                        journey.LAST_UPDATED_AT,
                        counts.users(),
                        counts.missingUsers(),
                        counts.crossProductUsers())
                .from(source)
                .where(condition)
                .orderBy(summaryOrder(journey.NAME, journey.RESOURCE_ID, counts.users(), query))
                .limit(query.pageSize())
                .offset(offset(query))
                .fetch(record -> journeySummary(record, journey, counts));
    }

    private List<ElevateUserSummary> userSummaries(
            ElevateUsers user, Field<Long> journeyCount, Condition condition, ElevateReadQuery query) {
        return userSummaries(user, journeyCount, user, condition, query);
    }

    private List<ElevateUserSummary> userSummaries(
            ElevateUsers user, Field<Long> journeyCount, Table<?> source, Condition condition, ElevateReadQuery query) {
        return dsl.select(
                        user.RESOURCE_ID,
                        user.PRODUCT_ID,
                        user.NAME,
                        user.DESCRIPTION,
                        user.CREATED_AT,
                        user.LAST_UPDATED_AT,
                        journeyCount)
                .from(source)
                .where(condition)
                .orderBy(summaryOrder(user.NAME, user.RESOURCE_ID, journeyCount, query))
                .limit(query.pageSize())
                .offset(offset(query))
                .fetch(record -> userSummary(record, user, journeyCount));
    }

    private static ElevateProductSummary productSummary(Record record, ElevateProducts product, ProductCounts counts) {
        return new ElevateProductSummary(
                record.get(product.RESOURCE_ID),
                record.get(product.SLUG),
                record.get(product.NAME),
                record.get(product.CUSTOMER),
                record.get(product.CREATED_AT),
                record.get(product.LAST_UPDATED_AT),
                record.get(counts.journeys()),
                record.get(counts.users()),
                record.get(counts.assignments()));
    }

    private static ElevateJourneySummary journeySummary(Record record, ElevateJourneys journey, JourneyCounts counts) {
        return new ElevateJourneySummary(
                record.get(journey.RESOURCE_ID),
                record.get(journey.SLUG),
                record.get(journey.NAME),
                record.get(journey.PRODUCT_ID),
                record.get(journey.PRODUCT_SLUG),
                record.get(journey.USER_DESCRIPTION),
                record.get(journey.PRIMARY_PROBLEMS),
                record.get(journey.CREATED_AT),
                record.get(journey.LAST_UPDATED_AT),
                record.get(counts.users()),
                record.get(counts.missingUsers()),
                record.get(counts.crossProductUsers()));
    }

    private static ElevateUserSummary userSummary(Record record, ElevateUsers user, Field<Long> journeyCount) {
        return new ElevateUserSummary(
                record.get(user.RESOURCE_ID),
                record.get(user.PRODUCT_ID),
                record.get(user.NAME),
                record.get(user.DESCRIPTION),
                record.get(user.CREATED_AT),
                record.get(user.LAST_UPDATED_AT),
                record.get(journeyCount));
    }

    private static ProductCounts productCounts(ElevateProducts product) {
        ElevateJourneys journey = ELEVATE_JOURNEYS.as("product_count_journey");
        ElevateUsers user = ELEVATE_USERS.as("product_count_user");
        ElevateJourneys assignmentJourney = ELEVATE_JOURNEYS.as("product_assignment_journey");
        var relation = ELEVATE_JOURNEY_USERS.as("product_assignment_relation");
        ElevateUsers assignmentUser = ELEVATE_USERS.as("product_assignment_user");
        Field<Long> journeys =
                countField(selectCount().from(journey).where(journey.PRODUCT_ID.eq(product.RESOURCE_ID)));
        Field<Long> users = countField(selectCount().from(user).where(user.PRODUCT_ID.eq(product.RESOURCE_ID)));
        Field<Long> assignments = countField(selectCount()
                .from(assignmentJourney)
                .join(relation)
                .on(relation.JOURNEY_ID.eq(assignmentJourney.RESOURCE_ID))
                .join(assignmentUser)
                .on(assignmentUser
                        .RESOURCE_ID
                        .eq(relation.USER_ID)
                        .and(assignmentUser.PRODUCT_ID.eq(assignmentJourney.PRODUCT_ID)))
                .where(assignmentJourney.PRODUCT_ID.eq(product.RESOURCE_ID)));
        return new ProductCounts(journeys, users, assignments);
    }

    private static JourneyCounts journeyCounts(ElevateJourneys journey) {
        var validRelation = ELEVATE_JOURNEY_USERS.as("valid_journey_relation");
        ElevateUsers validUser = ELEVATE_USERS.as("valid_journey_user");
        var missingRelation = ELEVATE_JOURNEY_USERS.as("missing_journey_relation");
        ElevateUsers missingUser = ELEVATE_USERS.as("missing_journey_user");
        var crossRelation = ELEVATE_JOURNEY_USERS.as("cross_journey_relation");
        ElevateUsers crossUser = ELEVATE_USERS.as("cross_journey_user");
        Field<Long> users = countField(selectCount()
                .from(validRelation)
                .join(validUser)
                .on(validUser.RESOURCE_ID.eq(validRelation.USER_ID).and(validUser.PRODUCT_ID.eq(journey.PRODUCT_ID)))
                .where(validRelation.JOURNEY_ID.eq(journey.RESOURCE_ID)));
        Field<Long> missingUsers = countField(selectCount()
                .from(missingRelation)
                .leftJoin(missingUser)
                .on(missingUser.RESOURCE_ID.eq(missingRelation.USER_ID))
                .where(missingRelation.JOURNEY_ID.eq(journey.RESOURCE_ID).and(missingUser.RESOURCE_ID.isNull())));
        Field<Long> crossProductUsers = countField(selectCount()
                .from(crossRelation)
                .join(crossUser)
                .on(crossUser.RESOURCE_ID.eq(crossRelation.USER_ID))
                .where(crossRelation
                        .JOURNEY_ID
                        .eq(journey.RESOURCE_ID)
                        .and(crossUser.PRODUCT_ID.ne(journey.PRODUCT_ID))));
        return new JourneyCounts(users, missingUsers, crossProductUsers);
    }

    private static Field<Long> userJourneyCount(ElevateUsers user) {
        var relation = ELEVATE_JOURNEY_USERS.as("user_journey_count_relation");
        ElevateJourneys journey = ELEVATE_JOURNEYS.as("user_journey_count_journey");
        return countField(selectCount()
                .from(relation)
                .join(journey)
                .on(journey.RESOURCE_ID.eq(relation.JOURNEY_ID).and(journey.PRODUCT_ID.eq(user.PRODUCT_ID)))
                .where(relation.USER_ID.eq(user.RESOURCE_ID)));
    }

    private static Field<Long> countField(Select<? extends Record1<Integer>> query) {
        return field(query).cast(Long.class);
    }

    private static Condition productSearch(ElevateProducts product, ElevateReadQuery query) {
        Condition condition = exactStringId(product.RESOURCE_ID, query.exactId());
        if (query.query().isBlank()) {
            return condition;
        }
        return condition.and(product.NAME
                .containsIgnoreCase(query.query())
                .or(product.SLUG.containsIgnoreCase(query.query()))
                .or(product.RESOURCE_ID.containsIgnoreCase(query.query()))
                .or(product.CUSTOMER.containsIgnoreCase(query.query())));
    }

    private static Condition journeySearch(ElevateJourneys journey, ElevateReadQuery query) {
        Condition condition = exactStringId(journey.RESOURCE_ID, query.exactId());
        if (query.query().isBlank()) {
            return condition;
        }
        return condition.and(journey.NAME
                .containsIgnoreCase(query.query())
                .or(journey.SLUG.containsIgnoreCase(query.query()))
                .or(journey.RESOURCE_ID.containsIgnoreCase(query.query())));
    }

    private static Condition userSearch(ElevateUsers user, ElevateReadQuery query) {
        Condition condition = exactUuidId(user.RESOURCE_ID, query.exactId());
        if (query.query().isBlank()) {
            return condition;
        }
        return condition.and(user.NAME
                .containsIgnoreCase(query.query())
                .or(user.RESOURCE_ID.cast(String.class).containsIgnoreCase(query.query())));
    }

    private static Condition exactStringId(Field<String> id, @Nullable String exactId) {
        return exactId == null ? noCondition() : id.eq(exactId);
    }

    private static Condition exactUuidId(Field<UUID> id, @Nullable String exactId) {
        if (exactId == null) {
            return noCondition();
        }
        try {
            return id.eq(UUID.fromString(exactId));
        } catch (IllegalArgumentException ignored) {
            return falseCondition();
        }
    }

    private static Condition relationshipFilter(Field<Long> relationships, ElevateReadQuery query) {
        return switch (query.relationship()) {
            case ALL -> noCondition();
            case LINKED -> relationships.gt(0L);
            case UNASSIGNED -> relationships.eq(0L);
        };
    }

    private static List<SortField<?>> summaryOrder(
            Field<String> name, Field<?> id, Field<Long> relationships, ElevateReadQuery query) {
        if (query.sort() == ElevateSort.RELATIONSHIPS) {
            return List.of(
                    ordered(relationships, query.direction()), lower(name).asc(), id.asc());
        }
        return List.of(ordered(lower(name), query.direction()), ordered(id, query.direction()));
    }

    private static SortField<?> ordered(Field<?> field, ElevateDirection direction) {
        return direction == ElevateDirection.ASC ? field.asc() : field.desc();
    }

    private long count(org.jooq.TableLike<?> table, Condition condition) {
        return dsl.fetchCount(dsl.selectOne().from(table).where(condition));
    }

    private static long offset(ElevateReadQuery query) {
        return (long) query.page() * query.pageSize();
    }

    private static <T> Page<T> page(List<T> content, long totalElements, ElevateReadQuery query) {
        long totalPages = totalElements == 0 ? 0 : (totalElements + query.pageSize() - 1) / query.pageSize();
        return new Page<>(ImmutableList.copyOf(content), query.page(), totalPages, totalElements);
    }

    private void requireSnapshotVersion(UUID expected) {
        @Nullable UUID current = dsl.select(ELEVATE_SYNC_STATE.SNAPSHOT_VERSION)
                .from(ELEVATE_SYNC_STATE)
                .where(ELEVATE_SYNC_STATE.SINGLETON.isTrue())
                .fetchOne(ELEVATE_SYNC_STATE.SNAPSHOT_VERSION);
        if (!expected.equals(current)) {
            throw new ElevateSnapshotChangedException();
        }
    }

    private void requireProduct(String productId) {
        if (!dsl.fetchExists(ELEVATE_PRODUCTS, ELEVATE_PRODUCTS.RESOURCE_ID.eq(productId))) {
            throw new ElevateResourceNotFoundException("product");
        }
    }

    private void requireJourney(String journeyId) {
        if (!dsl.fetchExists(ELEVATE_JOURNEYS, ELEVATE_JOURNEYS.RESOURCE_ID.eq(journeyId))) {
            throw new ElevateResourceNotFoundException("journey");
        }
    }

    private void requireUser(UUID userId) {
        if (!dsl.fetchExists(ELEVATE_USERS, ELEVATE_USERS.RESOURCE_ID.eq(userId))) {
            throw new ElevateResourceNotFoundException("user");
        }
    }

    private void insertProducts(List<ElevateProduct> products, Map<String, JsonNode> payloads) {
        List<Query> inserts = new ArrayList<>(Math.min(products.size(), INSERT_BATCH_SIZE));
        for (ElevateProduct product : products) {
            addToBatch(
                    inserts,
                    dsl.insertInto(ELEVATE_PRODUCTS)
                            .set(ELEVATE_PRODUCTS.RESOURCE_ID, product.id())
                            .set(ELEVATE_PRODUCTS.SLUG, product.slug())
                            .set(ELEVATE_PRODUCTS.NAME, product.name())
                            .set(ELEVATE_PRODUCTS.CUSTOMER, product.customer())
                            .set(ELEVATE_PRODUCTS.CREATED_AT, product.createdAt())
                            .set(ELEVATE_PRODUCTS.LAST_UPDATED_AT, product.lastUpdatedAt())
                            .set(ELEVATE_PRODUCTS.PAYLOAD, jsonb(payloadOrFallback(payloads, product.id(), product))));
        }
        executeBatch(inserts);
    }

    private void insertUsers(List<ElevateUser> users, Map<UUID, JsonNode> payloads) {
        List<Query> inserts = new ArrayList<>(Math.min(users.size(), INSERT_BATCH_SIZE));
        for (ElevateUser user : users) {
            addToBatch(
                    inserts,
                    dsl.insertInto(ELEVATE_USERS)
                            .set(ELEVATE_USERS.RESOURCE_ID, user.id())
                            .set(ELEVATE_USERS.PRODUCT_ID, user.productId())
                            .set(ELEVATE_USERS.NAME, user.name())
                            .set(ELEVATE_USERS.DESCRIPTION, user.description())
                            .set(ELEVATE_USERS.CREATED_AT, user.createdAt())
                            .set(ELEVATE_USERS.LAST_UPDATED_AT, user.lastUpdatedAt())
                            .set(ELEVATE_USERS.PAYLOAD, jsonb(payloadOrFallback(payloads, user.id(), user))));
        }
        executeBatch(inserts);
    }

    private void insertJourneys(List<ElevateJourney> journeys, Map<String, JsonNode> payloads) {
        List<Query> inserts = new ArrayList<>(Math.min(journeys.size(), INSERT_BATCH_SIZE));
        for (ElevateJourney journey : journeys) {
            addToBatch(
                    inserts,
                    dsl.insertInto(ELEVATE_JOURNEYS)
                            .set(ELEVATE_JOURNEYS.RESOURCE_ID, journey.id())
                            .set(ELEVATE_JOURNEYS.SLUG, journey.slug())
                            .set(ELEVATE_JOURNEYS.NAME, journey.name())
                            .set(ELEVATE_JOURNEYS.PRODUCT_ID, journey.productId())
                            .set(ELEVATE_JOURNEYS.PRODUCT_SLUG, journey.productSlug())
                            .set(ELEVATE_JOURNEYS.USER_DESCRIPTION, journey.userDescription())
                            .set(ELEVATE_JOURNEYS.PRIMARY_PROBLEMS, journey.primaryProblems())
                            .set(ELEVATE_JOURNEYS.CREATED_AT, journey.createdAt())
                            .set(ELEVATE_JOURNEYS.LAST_UPDATED_AT, journey.lastUpdatedAt())
                            .set(ELEVATE_JOURNEYS.PAYLOAD, jsonb(payloadOrFallback(payloads, journey.id(), journey))));
        }
        executeBatch(inserts);
    }

    private void insertJourneyUsers(List<ElevateJourney> journeys) {
        List<Query> inserts = new ArrayList<>(INSERT_BATCH_SIZE);
        for (ElevateJourney journey : journeys) {
            for (UUID userId : new LinkedHashSet<>(journey.userIds())) {
                addToBatch(
                        inserts,
                        dsl.insertInto(ELEVATE_JOURNEY_USERS)
                                .set(ELEVATE_JOURNEY_USERS.JOURNEY_ID, journey.id())
                                .set(ELEVATE_JOURNEY_USERS.USER_ID, userId));
            }
        }
        executeBatch(inserts);
    }

    private void refreshIntegrityItems() {
        dsl.insertInto(
                        ELEVATE_INTEGRITY_ITEMS,
                        ELEVATE_INTEGRITY_ITEMS.TYPE,
                        ELEVATE_INTEGRITY_ITEMS.JOURNEY_ID,
                        ELEVATE_INTEGRITY_ITEMS.JOURNEY_NAME,
                        ELEVATE_INTEGRITY_ITEMS.JOURNEY_PRODUCT_ID,
                        ELEVATE_INTEGRITY_ITEMS.USER_ID,
                        ELEVATE_INTEGRITY_ITEMS.USER_NAME,
                        ELEVATE_INTEGRITY_ITEMS.USER_PRODUCT_ID,
                        ELEVATE_INTEGRITY_ITEMS.SORT_NAME,
                        ELEVATE_INTEGRITY_ITEMS.SORT_ID,
                        ELEVATE_INTEGRITY_ITEMS.SEARCH_TEXT)
                .select(dsl.select(
                                ELEVATE_INTEGRITY_ITEM_SOURCE.TYPE,
                                ELEVATE_INTEGRITY_ITEM_SOURCE.JOURNEY_ID,
                                ELEVATE_INTEGRITY_ITEM_SOURCE.JOURNEY_NAME,
                                ELEVATE_INTEGRITY_ITEM_SOURCE.JOURNEY_PRODUCT_ID,
                                ELEVATE_INTEGRITY_ITEM_SOURCE.USER_ID,
                                ELEVATE_INTEGRITY_ITEM_SOURCE.USER_NAME,
                                ELEVATE_INTEGRITY_ITEM_SOURCE.USER_PRODUCT_ID,
                                ELEVATE_INTEGRITY_ITEM_SOURCE.SORT_NAME,
                                ELEVATE_INTEGRITY_ITEM_SOURCE.SORT_ID,
                                ELEVATE_INTEGRITY_ITEM_SOURCE.SEARCH_TEXT)
                        .from(ELEVATE_INTEGRITY_ITEM_SOURCE))
                .execute();
    }

    private void addToBatch(List<Query> batch, Query query) {
        batch.add(query);
        if (batch.size() == INSERT_BATCH_SIZE) {
            executeBatch(batch);
        }
    }

    private void executeBatch(List<Query> batch) {
        if (!batch.isEmpty()) {
            dsl.batch(batch).execute();
            batch.clear();
        }
    }

    private static <K> Object payloadOrFallback(Map<K, JsonNode> payloads, K id, Object fallback) {
        @Nullable JsonNode payload = payloads.get(id);
        return payload == null ? fallback : payload;
    }

    private JSONB jsonb(Object value) {
        return JSONB.valueOf(toJson(value));
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

    private record ProductCounts(Field<Long> journeys, Field<Long> users, Field<Long> assignments) {}

    private record JourneyCounts(Field<Long> users, Field<Long> missingUsers, Field<Long> crossProductUsers) {}
}
