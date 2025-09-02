package com.coreeng.supportbot.wiremock;

import java.util.Map;

import com.coreeng.supportbot.testkit.MessageTs;
import org.apache.commons.text.StringSubstitutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.coreeng.supportbot.Config;
import com.coreeng.supportbot.testkit.MessageToGet;
import com.coreeng.supportbot.testkit.ReactionAddedExpectation;
import com.coreeng.supportbot.testkit.Stub;
import com.coreeng.supportbot.testkit.StubWithResult;
import com.coreeng.supportbot.testkit.ThreadMessagePostedExpectation;
import com.coreeng.supportbot.testkit.UserProfileToGet;
import com.coreeng.supportbot.testkit.ViewsOpenExpectation;
import com.coreeng.supportbot.testkit.matcher.UrlDecodedPattern;
import com.github.tomakehurst.wiremock.WireMockServer;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.and;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;


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
            .withFormParam("timestamp", equalTo(expectation.ts().toString()))
            .willReturn(okJson("""
                {"ok":true}
                """))
        );
        return Stub.builder()
            .mapping(stubMapping)
            .wireMockServer(this)
            .build();
    }

    public <T> StubWithResult<T> stubMessagePosted(ThreadMessagePostedExpectation<T> expectation) {
        StubMapping mapping = givenThat(expectation.receiver().configureStub(post("/api/chat.postMessage"))
            .withFormParam("channel", equalTo(expectation.channelId()))
            .withFormParam("thread_ts", equalTo(expectation.threadTs().toString()))
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
        return StubWithResult.<T>builder()
            .mapping(mapping)
            .wireMockServer(this)
            .receiver(expectation.receiver())
            .build();
    }

    public Stub stubGetPermalink(String channelId, MessageTs ts, String permalink) {
        StubMapping stubMapping = givenThat(post("/api/chat.getPermalink")
            .withFormParam("channel", equalTo(channelId))
            .withFormParam("message_ts", equalTo(ts.toString()))
            .willReturn(okJson(StringSubstitutor.replace("""
                {
                  "ok": true,
                  "channel": "${channelId}",
                  "permalink": "${permalink}"
                }
                """, Map.of(
                "channelId", channelId,
                "permalink", permalink
            ))))
        );
        return Stub.builder()
            .mapping(stubMapping)
            .wireMockServer(this)
            .build();
    }

    public Stub stubGetPermalink(String channelId, MessageTs ts) {
        return stubGetPermalink(channelId, ts, "https://slack.com/messages/" + channelId + "/" + ts);
    }

    public Stub stubGetMessage(MessageToGet message) {
        StubMapping stubMapping = givenThat(post("/api/conversations.history")
            .withFormParam("channel", equalTo(message.channelId()))
            .withFormParam("limit", equalTo("1"))
            .withFormParam("oldest", equalTo(message.ts().toString()))
            .withFormParam("inclusive", equalTo("1"))
            .withFormParam("include_all_metadata", equalTo("0"))
            .willReturn(okJson(StringSubstitutor.replace("""
                    {
                        "ok": true,
                        "oldest": "${ts}",
                        "messages": [
                            {
                                "user": "${user}",
                                "team": "${team}",
                                "type": "message",
                                "ts": "${ts}",
                                "thread_ts": "${threadTs}",
                                "blocks": ${blocks}
                            }
                        ]
                    }
                    """, Map.of(
                        "user", message.user(),
                        "team", message.team(),
                        "blocks", message.blocksJson(),
                        "ts", message.ts(),
                        "threadTs", message.threadTs()
                    )))
        ));
        return Stub.builder()
            .mapping(stubMapping)
            .wireMockServer(this)
            .build();
    }

    public Stub stubGetUserProfileById(UserProfileToGet userProfile) {
        StubMapping stubMapping = givenThat(post("/api/users.profile.get")
            .withFormParam("user", equalTo(userProfile.userId()))
            .willReturn(okJson(StringSubstitutor.replace("""
                {
                    "ok": true,
                    "profile": {
                        "email": "${email}"
                    }
                }
                """, Map.of(
                    "userId", userProfile.userId(),
                    "email", userProfile.email()
                ))))
        );
        return Stub.builder()
            .mapping(stubMapping)
            .wireMockServer(this)
            .build();
    }

    public <T> StubWithResult<T> stubViewsOpen(ViewsOpenExpectation<T> expectation) {
        StubMapping mapping = givenThat(expectation.receiver().configureStub(post("/api/views.open"))
            .withFormParam("trigger_id", equalTo(expectation.triggerId()))
            .withFormParam("view", new UrlDecodedPattern(and(
                matchingJsonPath("$.type", equalTo(expectation.viewType())),
                matchingJsonPath("$.callback_id", equalTo(expectation.viewCallbackId()))
            )))
            .willReturn(aResponse()
                .withTransformers("response-template")
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {{formData request.body 'formArgs' urlDecode=true}}
                    {{parseJson formArgs.view 'formArgView'}}
                    {
                      "ok": true,
                      "view": {
                        "id":"UNSET_BY_TESTS",
                        "team_id":"UNSET_BY_TESTS",
                        "root_view_id":"UNSET_BY_TESTS",
                        "app_id":"UNSET_BY_TESTS",
                        "external_id":"",
                        "app_installed_team_id":"UNSET_BY_TESTS",
                        "bot_id":"UNSET_BY_TESTS",
                        "hash":"UNSET_BY_TESTS",
                        "type":"modal",
                        "callback_id":"ticket-summary",
                        "blocks": {{toJson formArgView.blocks}},
                        "private_metadata": {{toJson formArgView.private_metadata}},
                        "title": {{toJson formArgView.title}},
                        "close": {{toJson formArgView.close}},
                        "submit": {{toJson formArgView.submit}}
                      }
                    }
                    """))
        );
        return StubWithResult.<T>builder()
            .mapping(mapping)
            .wireMockServer(this)
            .receiver(expectation.receiver())
            .build();
    }
}
