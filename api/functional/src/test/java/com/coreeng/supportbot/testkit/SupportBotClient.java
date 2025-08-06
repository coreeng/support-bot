package com.coreeng.supportbot.testkit;

import com.coreeng.supportbot.wiremock.SlackWiremock;
import io.restassured.filter.log.LogDetail;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import static io.restassured.RestAssured.given;

@RequiredArgsConstructor
public class SupportBotClient {
    private final String baseUrl;
    private final SlackWiremock slackWiremock;

    public void assertQueryExistsByMessageRef(@NonNull String channelId, @NonNull String messageTs) {
        given()
            .when()
            .queryParam("channelId", channelId)
            .queryParam("messageTs", messageTs)
            .get(baseUrl + "/query")
            .then()
            .log().ifValidationFails(LogDetail.ALL, true)
            .statusCode(200);
    }

    public void assertTicketExists(TicketMessage ticketMessage) {
        slackWiremock.stubGetPermalink(ticketMessage.channelId(), ticketMessage.queryTs());
        given()
            .when()
            .get(baseUrl + "/ticket/{id}", ticketMessage.ticketId())
            .then()
            .log().ifValidationFails(LogDetail.ALL, true)
            .statusCode(200);
    }
}
