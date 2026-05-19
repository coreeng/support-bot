package com.coreeng.supportbot.testkit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static io.restassured.RestAssured.given;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
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

            given().formParam("trigger_id", "TRIGGER123")
                    .formParam("view", """
                            {"callback_id":"ticket-summary","blocks":[],"private_metadata":"pm","title":{"type":"plain_text","text":"Summary"},"close":{"type":"plain_text","text":"Close"},"submit":{"type":"plain_text","text":"Submit"}}
                            """)
                    .when()
                    .post("http://localhost:" + remoteServer.port() + "/api/views.open")
                    .then()
                    .statusCode(200)
                    .body("ok", org.hamcrest.Matchers.equalTo(true))
                    .body("view.callback_id", org.hamcrest.Matchers.equalTo("ticket-summary"));
        } finally {
            slackWiremock.stop();
            remoteServer.stop();
        }
    }

    @Test
    void remoteModeSupportsViewsOpenStubRegistration() {
        WireMockServer remoteServer =
                new WireMockServer(WireMockConfiguration.options().dynamicPort());
        remoteServer.start();

        SlackWiremock slackWiremock =
                new SlackWiremock(slackConfig(0, "remote", "http", "localhost", remoteServer.port()));

        try {
            slackWiremock.start();
            awaitUntilResponsive("http://localhost:" + remoteServer.port());

            StubWithResult<String> stub = slackWiremock.stubViewsOpen(ViewsOpenExpectation.<String>builder()
                    .description("remote: views.open")
                    .triggerId("TRIGGER123")
                    .viewCallbackId("ticket-summary")
                    .receiver(new StubWithResult.Receiver<>() {
                        @Override
                        public MappingBuilder configureStub(MappingBuilder stubBuilder) {
                            return stubBuilder;
                        }

                        @Override
                        public String assertAndExtractResult(ServeEvent servedStub) {
                            return servedStub
                                    .getRequest()
                                    .formParameter("trigger_id")
                                    .firstValue();
                        }
                    })
                    .build());

            given().formParam("trigger_id", "TRIGGER123")
                    .formParam("view", """
                            {"type":"modal","callback_id":"ticket-summary","blocks":[],"private_metadata":"pm","title":{"type":"plain_text","text":"Summary"},"close":{"type":"plain_text","text":"Close"},"submit":{"type":"plain_text","text":"Submit"}}
                            """)
                    .when()
                    .post("http://localhost:" + remoteServer.port() + "/api/views.open")
                    .then()
                    .statusCode(200)
                    .body("ok", org.hamcrest.Matchers.equalTo(true));

            stub.assertIsCalled();
        } finally {
            slackWiremock.stop();
            remoteServer.stop();
        }
    }

    @Test
    void remoteModeSupportsMessagePostedAndUpdatedStubRegistration() {
        WireMockServer remoteServer =
                new WireMockServer(WireMockConfiguration.options().dynamicPort());
        remoteServer.start();

        SlackWiremock slackWiremock =
                new SlackWiremock(slackConfig(0, "remote", "http", "localhost", remoteServer.port()));

        try {
            slackWiremock.start();
            awaitUntilResponsive("http://localhost:" + remoteServer.port());

            MessageTs threadTs = MessageTs.now();
            MessageTs messageTs = MessageTs.now();

            StubWithResult<String> postedStub =
                    slackWiremock.stubMessagePosted(ThreadMessagePostedExpectation.<String>builder()
                            .description("remote: chat.postMessage")
                            .channelId("C1234567891")
                            .threadTs(threadTs)
                            .from(UserRole.supportBot)
                            .newMessageTs(messageTs)
                            .receiver(new StubWithResult.Receiver<>() {
                                @Override
                                public MappingBuilder configureStub(MappingBuilder stubBuilder) {
                                    return stubBuilder;
                                }

                                @Override
                                public String assertAndExtractResult(ServeEvent servedStub) {
                                    return servedStub
                                            .getRequest()
                                            .formParameter("thread_ts")
                                            .firstValue();
                                }
                            })
                            .build());

            given().formParam("channel", "C1234567891")
                    .formParam("thread_ts", threadTs.toString())
                    .formParam("text", "Ticket 123")
                    .formParam("attachments", "[]")
                    .formParam("blocks", "[]")
                    .when()
                    .post("http://localhost:" + remoteServer.port() + "/api/chat.postMessage")
                    .then()
                    .statusCode(200)
                    .body("ok", org.hamcrest.Matchers.equalTo(true))
                    .body("ts", org.hamcrest.Matchers.equalTo(messageTs.toString()));

            postedStub.assertIsCalled();

            StubWithResult<String> updatedStub =
                    slackWiremock.stubMessageUpdated(MessageUpdatedExpectation.<String>builder()
                            .description("remote: chat.update")
                            .channelId("C1234567891")
                            .ts(messageTs)
                            .threadTs(threadTs)
                            .receiver(new StubWithResult.Receiver<>() {
                                @Override
                                public MappingBuilder configureStub(MappingBuilder stubBuilder) {
                                    return stubBuilder;
                                }

                                @Override
                                public String assertAndExtractResult(ServeEvent servedStub) {
                                    return servedStub
                                            .getRequest()
                                            .formParameter("ts")
                                            .firstValue();
                                }
                            })
                            .build());

            given().formParam("channel", "C1234567891")
                    .formParam("ts", messageTs.toString())
                    .formParam("blocks", "[]")
                    .formParam("attachments", "[]")
                    .when()
                    .post("http://localhost:" + remoteServer.port() + "/api/chat.update")
                    .then()
                    .statusCode(200)
                    .body("ok", org.hamcrest.Matchers.equalTo(true))
                    .body("ts", org.hamcrest.Matchers.equalTo(messageTs.toString()));

            updatedStub.assertIsCalled();
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
