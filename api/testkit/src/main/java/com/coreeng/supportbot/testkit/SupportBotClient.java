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
import io.restassured.specification.RequestSpecification;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.jackson.Jacksonized;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

@RequiredArgsConstructor
public class SupportBotClient {
    private static final String TEST_BYPASS_USER = "test@functional.test";
    private static final String TEST_BYPASS_ROLE = "support";
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

    public AnalysisMethods analysis() {
        return new AnalysisMethods();
    }

    public TestMethods test() {
        return new TestMethods();
    }

    public TenantInsightsMethods tenantInsights() {
        return new TenantInsightsMethods();
    }

    private RequestSpecification request() {
        return given().config(REST_ASSURED_CONFIG)
                .header("X-Test-User", TEST_BYPASS_USER)
                .header("X-Test-Role", TEST_BYPASS_ROLE);
    }

    public void assertQueryExistsByMessageRef(@NonNull String channelId, @NonNull MessageTs ts) {
        request()
                .when()
                .queryParam("channelId", channelId)
                .queryParam("messageTs", ts.toString())
                .get(baseUrl + "/query")
                .then()
                .log()
                .ifValidationFails(LogDetail.ALL, true)
                .statusCode(200);
    }

    public void assertQueryDoesNotExistByMessageRef(@NonNull String channelId, @NonNull MessageTs ts) {
        request()
                .when()
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
            return request()
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
        return request()
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
        return request()
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
        io.restassured.response.Response response = request().when().get(baseUrl + "/ticket/{id}", ticketId);

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

    public class AnalysisMethods {
        public boolean enabled() {
            return request()
                    .when()
                    .get(baseUrl + "/analysis/enabled")
                    .then()
                    .log()
                    .ifValidationFails(LogDetail.ALL, true)
                    .statusCode(200)
                    .extract()
                    .as(AnalysisEnabledResponse.class)
                    .enabled();
        }

        public int runStatusCode(int days) {
            return request()
                    .when()
                    .post(baseUrl + "/analysis/run?days={days}", days)
                    .then()
                    .extract()
                    .statusCode();
        }

        public AnalysisStatusResponse status() {
            return request()
                    .when()
                    .get(baseUrl + "/analysis/status")
                    .then()
                    .log()
                    .ifValidationFails(LogDetail.ALL, true)
                    .statusCode(200)
                    .extract()
                    .as(AnalysisStatusResponse.class);
        }

        public SummaryDataResultsResponse results() {
            return request()
                    .when()
                    .get(baseUrl + "/summary-data/results")
                    .then()
                    .log()
                    .ifValidationFails(LogDetail.ALL, true)
                    .statusCode(200)
                    .extract()
                    .as(SummaryDataResultsResponse.class);
        }
    }

    public class TenantInsightsMethods {
        public ListResponse<RepoInsightsResponse> prStats() {
            return new ListResponse<>(request()
                    .when()
                    .get(baseUrl + "/tenant-insights/pr-stats")
                    .then()
                    .log()
                    .ifValidationFails(LogDetail.ALL, true)
                    .statusCode(200)
                    .extract()
                    .jsonPath()
                    .getList(".", RepoInsightsResponse.class));
        }

        public ListResponse<RepoInsightsResponse> prStats(LocalDate dateFrom, LocalDate dateTo) {
            return new ListResponse<>(request()
                    .queryParam("dateFrom", dateFrom.toString())
                    .queryParam("dateTo", dateTo.toString())
                    .when()
                    .get(baseUrl + "/tenant-insights/pr-stats")
                    .then()
                    .log()
                    .ifValidationFails(LogDetail.ALL, true)
                    .statusCode(200)
                    .extract()
                    .jsonPath()
                    .getList(".", RepoInsightsResponse.class));
        }

        public ListResponse<InFlightPrResponse> inFlightPrs() {
            return new ListResponse<>(request()
                    .when()
                    .get(baseUrl + "/tenant-insights/in-flight-prs")
                    .then()
                    .log()
                    .ifValidationFails(LogDetail.ALL, true)
                    .statusCode(200)
                    .extract()
                    .jsonPath()
                    .getList(".", InFlightPrResponse.class));
        }

        public EscalationBreakdownResponse escalationBreakdown(
                @Nullable LocalDate dateFrom, @Nullable LocalDate dateTo) {
            RequestSpecification requestSpecification = request();
            if (dateFrom != null) {
                requestSpecification = requestSpecification.queryParam("dateFrom", dateFrom.toString());
            }
            if (dateTo != null) {
                requestSpecification = requestSpecification.queryParam("dateTo", dateTo.toString());
            }
            return requestSpecification
                    .when()
                    .get(baseUrl + "/tenant-insights/escalation-breakdown")
                    .then()
                    .log()
                    .ifValidationFails(LogDetail.ALL, true)
                    .statusCode(200)
                    .extract()
                    .as(EscalationBreakdownResponse.class);
        }
    }

    public record ListResponse<T>(ImmutableList<T> items) {
        public ListResponse(List<T> items) {
            this(ImmutableList.copyOf(items));
        }
    }

    public TeamSuggestionsResponse getTeamSuggestions(long ticketId) {
        return request()
                .when()
                .get(baseUrl + "/ticket/{id}/team-suggestions", ticketId)
                .then()
                .log()
                .ifValidationFails(LogDetail.ALL, true)
                .statusCode(200)
                .extract()
                .as(TeamSuggestionsResponse.class);
    }

    public int getTeamSuggestionsStatusCode(long ticketId) {
        return request()
                .when()
                .get(baseUrl + "/ticket/{id}/team-suggestions", ticketId)
                .then()
                .extract()
                .statusCode();
    }

    /**
     * Look up a ticket by its Slack query message reference.
     *
     * @param channelId The channel ID where the ticket was created
     * @param queryTs   The query message timestamp
     * @return The ticket response, or null if not found
     */
    @Nullable public TicketResponse findTicketByQueryTs(@NonNull String channelId, @NonNull MessageTs queryTs) {
        io.restassured.response.Response response = request()
                .when()
                .queryParam("channelId", channelId)
                .queryParam("messageTs", queryTs.toString())
                .get(baseUrl + "/test/ticket/by-query");

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

    public class TestMethods {
        public TicketResponse createTicket(TicketToCreateRequest request) {
            return request()
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
            request()
                    .when()
                    .contentType(JSON)
                    .body(escalation)
                    .post(baseUrl + "/test/escalation")
                    .then()
                    .log()
                    .ifValidationFails(LogDetail.ALL, true)
                    .statusCode(200);
        }

        public void triggerPrTrackingPoll() {
            request()
                    .when()
                    .post(baseUrl + "/test/prtracking/poll")
                    .then()
                    .log()
                    .ifValidationFails(LogDetail.ALL, true)
                    .statusCode(200);
        }

        public void cleanupPrTrackingRecords() {
            request()
                    .when()
                    .post(baseUrl + "/test/prtracking/cleanup")
                    .then()
                    .log()
                    .ifValidationFails(LogDetail.ALL, true)
                    .statusCode(200);
        }

        public PrTrackingRecordResponse createPrTrackingRecord(PrTrackingToCreate request) {
            return request()
                    .when()
                    .contentType(JSON)
                    .body(request)
                    .post(baseUrl + "/test/prtracking/record")
                    .then()
                    .log()
                    .ifValidationFails(LogDetail.ALL, true)
                    .statusCode(200)
                    .extract()
                    .as(PrTrackingRecordResponse.class);
        }

        public PrTrackingRecordResponse getPrTrackingRecord(long id) {
            return request()
                    .when()
                    .get(baseUrl + "/test/prtracking/record/{id}", id)
                    .then()
                    .log()
                    .ifValidationFails(LogDetail.ALL, true)
                    .statusCode(200)
                    .extract()
                    .as(PrTrackingRecordResponse.class);
        }

        public PrTrackingRecordResponse closePrTrackingRecord(long id) {
            return request()
                    .when()
                    .post(baseUrl + "/test/prtracking/record/{id}/close", id)
                    .then()
                    .log()
                    .ifValidationFails(LogDetail.ALL, true)
                    .statusCode(200)
                    .extract()
                    .as(PrTrackingRecordResponse.class);
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

    @Getter
    @Jacksonized
    @Builder
    public static class TeamSuggestionsResponse {
        private ImmutableList<@NonNull String> suggestedTeams;
        private ImmutableList<@NonNull String> otherTeams;
    }

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

        @Nullable private String source;
    }

    @Builder
    @Getter
    @Jacksonized
    public static class PrTrackingToCreate {
        private long ticketId;

        @Nullable private String provider;

        private String githubRepo;
        private int prNumber;
        private Instant prCreatedAt;
        private Instant slaDeadline;
        private String owningTeam;

        @Nullable private Boolean canAutoCloseTicket;
    }

    @Builder
    @Getter
    @Jacksonized
    public static class PrTrackingRecordResponse {
        private long id;
        private long ticketId;
        private String provider;
        private String repo;
        private int prNumber;
        private Instant prCreatedAt;
        private Instant slaDeadline;
        private String owningTeam;
        private String status;
        private Instant closedAt;
        private Long escalationId;

        @Nullable private Duration slaRemaining;
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

    public record AnalysisEnabledResponse(boolean enabled) {}

    public record AnalysisStatusResponse(
            boolean running, @Nullable String error) {}

    public record SummaryDataResultsResponse(ImmutableList<SupportAreaResponse> supportAreas) {}

    public record SupportAreaResponse(ImmutableList<SummaryQueryResultResponse> queries) {}

    public record SummaryQueryResultResponse(String text, String timestamp, String ticketId) {}

    public record RepoInsightsResponse(
            String repo,
            String owningTeam,
            long prCount,
            long openCount,
            long escalatedCount,
            long breachedCount,
            double p50Seconds,
            double p90Seconds,
            double p99Seconds,
            boolean hasSla) {}

    public record EscalationBreakdownResponse(
            long totalPrTickets, long botEscalatedTickets, long manuallyEscalatedTickets) {}

    public record InFlightPrResponse(
            String githubRepo,
            int prNumber,
            String prUrl,
            String status,
            String waitingOn,
            Instant prCreatedAt,
            @Nullable Instant slaDeadline,
            @Nullable Long slaRemainingSeconds,
            @Nullable Instant lastReviewAt,
            String owningTeam,
            String owningTeamLabel,
            String ticketChannelId,
            String ticketQueryTs,
            @Nullable Instant escalatedAt,
            boolean hasSla) {}
}
