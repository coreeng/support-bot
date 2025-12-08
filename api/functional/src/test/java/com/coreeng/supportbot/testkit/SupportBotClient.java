package com.coreeng.supportbot.testkit;

import java.time.Instant;

import org.jspecify.annotations.NonNull;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.collect.ImmutableList;

import static io.restassured.RestAssured.given;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.ContentType;
import static io.restassured.http.ContentType.JSON;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.jackson.Jacksonized;

@RequiredArgsConstructor
public class SupportBotClient {
    private final static ObjectMapper objectMapper = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(JsonParser.Feature.INCLUDE_SOURCE_IN_LOCATION, true)
        .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
        .registerModule(new JavaTimeModule())
        .registerModule(new GuavaModule());
    private final static RestAssuredConfig restAssuredConfig = RestAssuredConfig.config()
        .objectMapperConfig(ObjectMapperConfig.objectMapperConfig()
            .jackson2ObjectMapperFactory((type, charset) -> objectMapper));


    private final String baseUrl;
    private final SlackWiremock slackWiremock;

    public TestMethods test() {
        return new TestMethods();
    }

    public void assertQueryExistsByMessageRef(@NonNull String channelId, @NonNull MessageTs ts) {
        given()
            .when()
            .queryParam("channelId", channelId)
            .queryParam("messageTs", ts.toString())
            .get(baseUrl + "/query")
            .then()
            .log().ifValidationFails(LogDetail.ALL, true)
            .statusCode(200);
    }

    public void assertQueryDoesNotExistByMessageRef(@NonNull String channelId, @NonNull MessageTs ts) {
        given()
            .when()
            .queryParam("channelId", channelId)
            .queryParam("messageTs", ts.toString())
            .get(baseUrl + "/query")
            .then()
            .log().ifValidationFails(LogDetail.ALL, true)
            .statusCode(404);
    }

    public TicketResponse assertTicketExists(SearchableForTicket ticket) {
        slackWiremock.stubGetPermalink(ticket.channelId(), ticket.queryTs());
        return given()
            .config(restAssuredConfig)
            .when()
            .get(baseUrl + "/ticket/{id}", ticket.ticketId())
            .then()
            .log().ifValidationFails(LogDetail.ALL, true)
            .statusCode(200)
            .extract().as(TicketResponse.class);
    }

    public class TestMethods {
        public TicketResponse createTicket(TicketToCreateRequest request) {
            return given()
                .config(restAssuredConfig)
                .when()
                .contentType(ContentType.JSON)
                .body(request)
                .post(baseUrl + "/test/ticket")
                .then()
                .log().ifValidationFails(LogDetail.ALL, true)
                .statusCode(200)
                .and()
                .extract().body().as(TicketResponse.class);
        }

        public void escalateTicket(EscalationToCreate escalation) {
            given()
                .config(restAssuredConfig)
                .when()
                .contentType(JSON)
                .body(escalation)
                .post(baseUrl + "/test/escalation")
                .then()
                .log().ifValidationFails(LogDetail.ALL, true)
                .statusCode(200);
        }
    }

    @Builder
    @Getter
    public static class TicketToCreateRequest {
        @NonNull
        private final String channelId;
        @NonNull
        private final MessageTs queryTs;
        @NonNull
        private final MessageTs createdMessageTs;
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
    }

    public record QueryResponse(String link, Instant date, MessageTs ts) {}
    public record TicketFormMessage(MessageTs ts) {}
    public record Team(String label, String code, ImmutableList<@NonNull String> types) {}
    @Builder
    @Getter
    @Jacksonized
    public static class Escalation {
        private String threadLink;
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
}
