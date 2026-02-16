package com.coreeng.supportbot.testkit;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.collect.ImmutableList;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.ContentType;
import java.time.Instant;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.jackson.Jacksonized;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor
public class SupportBotClient {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(JsonParser.Feature.INCLUDE_SOURCE_IN_LOCATION, true)
            .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
            .registerModule(new JavaTimeModule())
            .registerModule(new GuavaModule());
    private static final RestAssuredConfig REST_ASSURED_CONFIG = RestAssuredConfig.config()
            .objectMapperConfig(ObjectMapperConfig.objectMapperConfig()
                    .jackson2ObjectMapperFactory((type, charset) -> OBJECT_MAPPER));

    private final String baseUrl;
    private final SlackWiremock slackWiremock;

    public TestMethods test() {
        return new TestMethods();
    }

    public void assertQueryExistsByMessageRef(@NonNull String channelId, @NonNull MessageTs ts) {
        given().when()
                .queryParam("channelId", channelId)
                .queryParam("messageTs", ts.toString())
                .get(baseUrl + "/query")
                .then()
                .log()
                .ifValidationFails(LogDetail.ALL, true)
                .statusCode(200);
    }

    public void assertQueryDoesNotExistByMessageRef(@NonNull String channelId, @NonNull MessageTs ts) {
        given().when()
                .queryParam("channelId", channelId)
                .queryParam("messageTs", ts.toString())
                .get(baseUrl + "/query")
                .then()
                .log()
                .ifValidationFails(LogDetail.ALL, true)
                .statusCode(404);
    }

    public TicketResponse assertTicketExists(TicketByIdQuery query) {
        Stub getPermalinkStub =
                slackWiremock.stubGetPermalink("assertTicketExists: get permalink", query.channelId(), query.queryTs());
        Stub getMessageStub = slackWiremock.stubGetMessage(MessageToGet.builder()
                .description("assertTicketExists: get message")
                .ts(query.queryTs())
                .threadTs(query.queryTs())
                .channelId(query.channelId())
                .text(query.queryText())
                .blocksJson(query.queryBlocksJson())
                .build());
        try {
            return given().config(REST_ASSURED_CONFIG)
                    .when()
                    .get(baseUrl + "/ticket/{id}", query.ticketId())
                    .then()
                    .log()
                    .ifValidationFails(LogDetail.ALL, true)
                    .statusCode(200)
                    .extract()
                    .as(TicketResponse.class);
        } finally {
            getPermalinkStub.cleanUp(); // it's cached so might be not called
            getMessageStub.assertIsCalled();
        }
    }

    public TicketResponse updateTicket(long ticketId, UpdateTicketRequest request) {
        return given().config(REST_ASSURED_CONFIG)
                .when()
                .contentType(ContentType.JSON)
                .body(request)
                .patch(baseUrl + "/ticket/{id}", ticketId)
                .then()
                .log()
                .ifValidationFails(LogDetail.ALL, true)
                .statusCode(200)
                .extract()
                .as(TicketResponse.class);
    }

    public BulkReassignResponse bulkReassign(BulkReassignRequest request) {
        return given().config(REST_ASSURED_CONFIG)
                .when()
                .contentType(ContentType.JSON)
                .body(request)
                .post(baseUrl + "/assignment/bulk-reassign")
                .then()
                .log()
                .ifValidationFails(LogDetail.ALL, true)
                .statusCode(200)
                .extract()
                .as(BulkReassignResponse.class);
    }

    /**
     * Get a ticket by its ID.
     * Returns null if the ticket is not found (404).
     */
    @Nullable public TicketResponse getTicketById(long ticketId) {
        io.restassured.response.Response response =
                given().config(REST_ASSURED_CONFIG).when().get(baseUrl + "/ticket/{id}", ticketId);

        if (response.statusCode() == 404) {
            return null;
        }

        return response.then()
                .log()
                .ifValidationFails(LogDetail.ALL, true)
                .statusCode(200)
                .extract()
                .as(TicketResponse.class);
    }

    /**
     * Find the most recently created ticket by queryTs.
     * Uses the /ticket list endpoint and filters by queryTs.
     *
     * @param channelId The channel ID where the ticket was created
     * @param queryTs   The query message timestamp
     * @return The ticket response, or null if not found
     */
    @Nullable public TicketResponse findTicketByQueryTs(@NonNull String channelId, @NonNull MessageTs queryTs) {
        TicketListResponse response = given().config(REST_ASSURED_CONFIG)
                .when()
                .queryParam("pageSize", 100)
                .get(baseUrl + "/ticket")
                .then()
                .log()
                .ifValidationFails(LogDetail.ALL, true)
                .statusCode(200)
                .extract()
                .as(TicketListResponse.class);

        return response.content().stream()
                .filter(t -> t.query() != null
                        && t.query().ts() != null
                        && t.query().ts().equals(queryTs)
                        && channelId.equals(t.channelId()))
                .findFirst()
                .orElse(null);
    }

    @Getter
    @Jacksonized
    @Builder
    public static class TicketListResponse {
        private ImmutableList<@NonNull TicketResponse> content;
        private long page;
        private long totalPages;
        private long totalElements;
    }

    public class TestMethods {
        public TicketResponse createTicket(TicketToCreateRequest request) {
            return given().config(REST_ASSURED_CONFIG)
                    .when()
                    .contentType(ContentType.JSON)
                    .body(request)
                    .post(baseUrl + "/test/ticket")
                    .then()
                    .log()
                    .ifValidationFails(LogDetail.ALL, true)
                    .statusCode(200)
                    .and()
                    .extract()
                    .body()
                    .as(TicketResponse.class);
        }

        public void escalateTicket(EscalationToCreate escalation) {
            given().config(REST_ASSURED_CONFIG)
                    .when()
                    .contentType(JSON)
                    .body(escalation)
                    .post(baseUrl + "/test/escalation")
                    .then()
                    .log()
                    .ifValidationFails(LogDetail.ALL, true)
                    .statusCode(200);
        }
    }

    @Builder
    @Getter
    public static class TicketToCreateRequest {
        @NonNull private final String channelId;

        @NonNull private final MessageTs queryTs;

        @NonNull private final MessageTs createdMessageTs;
    }

    @Getter
    @Builder
    @Jacksonized
    public static class TicketResponse {
        private long id;
        private QueryResponse query;
        private TicketFormMessage formMessage;
        private String channelId;
        private String status;
        private String impact;
        private Team team;
        private ImmutableList<@NonNull String> tags;
        private ImmutableList<Ticket.@NonNull StatusLog> logs;
        private boolean escalated;
        private ImmutableList<@NonNull Escalation> escalations;
        private String assignedTo;
    }

    public record QueryResponse(String link, Instant date, MessageTs ts, String text) {}

    public record TicketFormMessage(MessageTs ts) {}

    public record Team(String label, String code, ImmutableList<@NonNull String> types) {}

    @Builder
    @Getter
    @Jacksonized
    public static class Escalation {
        private boolean hasThread;
        private Team team;
        private ImmutableList<@NonNull String> tags;
        private Instant openedAt;
        private Instant resolvedAt;
    }

    @Builder
    @Getter
    @Jacksonized
    public static class EscalationToCreate {
        private long ticketId;
        private String team;
        private MessageTs createdMessageTs;
        private ImmutableList<@NonNull String> tags;
    }

    @Builder
    @Getter
    @Jacksonized
    public static class UpdateTicketRequest {
        private final String status;
        private final String authorsTeam;
        private final ImmutableList<@NonNull String> tags;
        private final String impact;
    }

    @Builder
    @Getter
    @Jacksonized
    public static class BulkReassignRequest {
        private final ImmutableList<Long> ticketIds;
        private final String assignedTo;
    }

    @Builder
    @Getter
    @Jacksonized
    public static class BulkReassignResponse {
        private final int successCount;
        private final int failureCount;
        private final int skippedCount;
        private final ImmutableList<Long> successfulTicketIds;
        private final ImmutableList<Long> failedTicketIds;
        private final ImmutableList<Long> skippedTicketIds;
        private final String message;
    }
}
