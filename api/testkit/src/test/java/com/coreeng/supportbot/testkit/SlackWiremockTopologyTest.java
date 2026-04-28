package com.coreeng.supportbot.testkit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static io.restassured.RestAssured.given;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

class SlackWiremockTopologyTest {
    @Test
    void embeddedModeKeepsExistingDslWorking() {
        SlackWiremock slackWiremock = new SlackWiremock(slackConfig(0, null, null, null, null));

        slackWiremock.start();
        try {
            awaitUntilResponsive("http://localhost:" + slackWiremock.port());

            given().when()
                    .post("http://localhost:" + slackWiremock.port() + "/api/auth.test")
                    .then()
                    .statusCode(200);

            Stub stub = slackWiremock.stubConversationsOpen("embedded: open dm", "U123");
            given().formParam("users", "U123")
                    .when()
                    .post("http://localhost:" + slackWiremock.port() + "/api/conversations.open")
                    .then()
                    .statusCode(200);

            stub.assertIsCalled();
            slackWiremock.assertNoTestStubsRemaining();
        } finally {
            slackWiremock.stop();
        }
    }

    @Test
    void remoteModeUsesRemoteAdminAgainstExistingServer() {
        WireMockServer remoteServer =
                new WireMockServer(WireMockConfiguration.options().dynamicPort());
        remoteServer.start();

        SlackWiremock slackWiremock =
                new SlackWiremock(slackConfig(0, "remote", "http", "localhost", remoteServer.port()));

        try {
            slackWiremock.start();
            awaitUntilResponsive("http://localhost:" + remoteServer.port());

            given().when()
                    .post("http://localhost:" + remoteServer.port() + "/api/auth.test")
                    .then()
                    .statusCode(200);

            Stub stub = slackWiremock.stubConversationsOpen("remote: open dm", "U456");
            given().formParam("users", "U456")
                    .when()
                    .post("http://localhost:" + remoteServer.port() + "/api/conversations.open")
                    .then()
                    .statusCode(200);

            stub.assertIsCalled();
            slackWiremock.assertNoTestStubsRemaining();
        } finally {
            slackWiremock.stop();
            remoteServer.stop();
        }
    }

    @Test
    void remoteModeSupportsNftPermanentStubsAgainstPlainWireMockServer() {
        WireMockServer remoteServer =
                new WireMockServer(WireMockConfiguration.options().dynamicPort());
        remoteServer.start();

        SlackWiremock slackWiremock =
                new SlackWiremock(slackConfig(0, "remote", "http", "localhost", remoteServer.port()));

        try {
            slackWiremock.start();
            slackWiremock.permanent().setupAllNftStubs();
            awaitUntilResponsive("http://localhost:" + remoteServer.port());

            given().formParam("channel", "C1234567891")
                    .formParam("thread_ts", "1737123456.123456")
                    .formParam("text", "Ticket 123")
                    .formParam("blocks", "[]")
                    .when()
                    .post("http://localhost:" + remoteServer.port() + "/api/chat.postMessage")
                    .then()
                    .statusCode(200)
                    .body("ok", org.hamcrest.Matchers.equalTo(true))
                    .body("ts", org.hamcrest.Matchers.matchesPattern("[0-9]{10}\\.[0-9]{6}"));
        } finally {
            slackWiremock.stop();
            remoteServer.stop();
        }
    }

    @Test
    void cleanupHelpersStillWorkForDirectStubForUsage() {
        SlackWiremock slackWiremock = new SlackWiremock(slackConfig(0, null, null, null, null));

        slackWiremock.start();
        try {
            awaitUntilResponsive("http://localhost:" + slackWiremock.port());

            slackWiremock.stubFor(post("/api/conversations.replies")
                    .willReturn(aResponse().withStatus(200).withBody("{\"ok\":true,\"messages\":[]}")));

            given().when()
                    .post("http://localhost:" + slackWiremock.port() + "/api/conversations.replies")
                    .then()
                    .statusCode(200);

            slackWiremock.cleanupTestStubs();
            slackWiremock.assertNoTestStubsRemaining();
            slackWiremock.clearRequestJournal();
        } finally {
            slackWiremock.stop();
        }
    }

    private static Config.SlackMock slackConfig(
            int port, String mode, String remoteAdminScheme, String remoteAdminHost, Integer remoteAdminPort) {
        return new Config.SlackMock(
                port,
                "https://cecg.slack.com/",
                "CECG",
                "T0123456789",
                "USUPPORT123",
                "B0123456789",
                "S01234567ST",
                "C1234567891",
                List.of(),
                mode,
                remoteAdminScheme,
                remoteAdminHost,
                remoteAdminPort);
    }

    private static void awaitUntilResponsive(String baseUrl) {
        Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> {
            try {
                given().when().get(baseUrl + "/__admin").then().statusCode(200);
                return true;
            } catch (RuntimeException ignored) {
                return false;
            }
        });
    }
}
