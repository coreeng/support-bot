package com.coreeng.supportbot.wiremock;

import com.coreeng.supportbot.Config;
import com.coreeng.supportbot.testkit.ReactionAddedExpectation;
import com.coreeng.supportbot.testkit.Stub;
import com.coreeng.supportbot.testkit.StubWithResult;
import com.coreeng.supportbot.testkit.ThreadMessagePostedExpectation;
import com.coreeng.supportbot.testkit.TicketMessage;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import org.apache.commons.text.StringSubstitutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;


/**
 * Wiremock implementation for Slack service.
 * Handles mocking of Slack API endpoints.
 */
public class SlackWiremock extends WireMockServer {
    private static final Logger logger = LoggerFactory.getLogger(SlackWiremock.class);

    private final Config.SlackMock config;

    public SlackWiremock(Config.SlackMock config) {
        super(WireMockConfiguration.options()
            .port(config.port()));
        this.config = config;
    }

    @Override
    public void start() {
        super.start();
        setupAppInitMocks();
        logger.info("Started Slack Wiremock server on port {}", this.port());
    }

    @Override
    public void stop() {
        super.stop();
        logger.info("Stopped Slack Wiremock server");
    }

    private void setupAppInitMocks() {
        logger.info("Setting up initial Slack API stubs");
        stubAuthTest();
    }

    public void stubAuthTest() {
        givenThat(post("/api/auth.test")
            .willReturn(okJson(new StringSubstitutor(Map.of(
                "url", config.serverUrl(),
                "team", config.team(),
                "teamId", config.teamId(),
                "userId", config.userId(),
                "botId", config.botId()
            )).replace("""
                {
                  "ok":true,
                  "url":"${url}",
                  "team":"${team}",
                  "user":"core_support",
                  "team_id":"${teamId}",
                  "user_id":"${userId}",
                  "bot_id":"${botId}",
                  "is_enterprise_install":false}"""))));
    }

    public Stub stubReactionAdd(ReactionAddedExpectation expectation) {
        StubMapping stubMapping = givenThat(post("/api/reactions.add")
            .withFormParam("name", equalTo(expectation.reaction()))
            .withFormParam("channel", equalTo(expectation.channelId()))
            .withFormParam("timestamp", equalTo(expectation.ts()))
            .willReturn(okJson("""
                {"ok":true}
                """))
        );
        return Stub.builder()
            .mapping(stubMapping)
            .wireMockServer(this)
            .build();
    }

    public StubWithResult<TicketMessage> stubMessagePosted(ThreadMessagePostedExpectation<TicketMessage> expectation) {
        StubMapping mapping = givenThat(expectation.receiver().configureStub(post("/api/chat.postMessage"))
            .withFormParam("channel", equalTo(expectation.channelId()))
            .withFormParam("thread_ts", equalTo(expectation.threadTs()))
            .willReturn(aResponse()
                .withTransformers("response-template")
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withTransformerParameter("newTs", expectation.newMessageTs())
                .withBody("""
                    {{formData request.body 'formArgs' urlDecode=true}}
                    {
                      "ok": true,
                      "channel": "{{formArgs.channel}}",
                      "ts": "{{parameters.newTs}}",
                      "message": {
                        "user": "UNSET_BY_TESTS",
                        "parent_user_id": "UNSET_BY_TESTS",
                        "bot_id": "UNSET_BY_TESTS",
                        "app_id": "UNSET_BY_TESTS",
                        "team": "UNSET_BY_TESTS",
                        "type": "message",
                        "ts": "{{parameters.newTs}}",
                        "thread_ts": "{{formArgs.thread_ts}}",
                        "text": "{{formArgs.text}}",
                        "attachments": {{formArgs.attachments}},
                        "blocks": {{formArgs.blocks}}
                      }
                    }
                    """))
        );
        return StubWithResult.<TicketMessage>builder()
            .mapping(mapping)
            .wireMockServer(this)
            .receiver(expectation.receiver())
            .build();
    }

    public Stub stubGetPermalink(String channelId, String ts) {
        StubMapping stubMapping = givenThat(post("/api/chat.getPermalink")
            .withFormParam("channel", equalTo(channelId))
            .withFormParam("message_ts", equalTo(ts))
            .willReturn(okJson(StringSubstitutor.replace("""
                {
                  "ok": true,
                  "channel": "${channelId}",
                  "permalink": "https://slack.com/messages/${channelId}/${ts}"
                }
                """, Map.of(
                "channelId", channelId,
                "ts", ts
            ))))
        );
        return Stub.builder()
            .mapping(stubMapping)
            .wireMockServer(this)
            .build();
    }
}
