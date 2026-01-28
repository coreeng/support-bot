package com.coreeng.supportbot.testkit;

import java.net.Socket;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.text.StringSubstitutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.coreeng.supportbot.testkit.matcher.UrlDecodedPattern;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.and;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;


/**
 * Wiremock implementation for Slack service.
 * Handles mocking of Slack API endpoints.
 */
public class SlackWiremock extends WireMockServer {
    private static final Logger logger = LoggerFactory.getLogger(SlackWiremock.class);

    private final Config.SlackMock config;
    private final Set<UUID> permanentStubIds = new HashSet<>();

    public SlackWiremock(Config.SlackMock config) {
        super(WireMockConfiguration.options()
            .port(config.port())
            .globalTemplating(true)
            .maxRequestJournalEntries(1000)
            .extensions(new MessageTsHelperExtension()));
        this.config = config;
    }

    @Override
    public void start() {
        super.start();
        setupAppInitMocks();
        capturePermanentStubs();
        logger.info("Started Slack Wiremock server on port {}", this.port());
    }

    @Override
    public void stop() {
        super.stop();
        logger.info("Stopped Slack Wiremock server");
    }

    private void setupAppInitMocks() {
        logger.info("Setting up initial Slack API stubs");
        stubAuthTest("initial mock");
    }

    private void capturePermanentStubs() {
        getStubMappings().forEach(stub -> permanentStubIds.add(stub.getId()));
        logger.info("Captured {} permanent stubs", permanentStubIds.size());
    }

    /**
     * Asserts that no test stubs remain after a test.
     * Permanent stubs (set up at server start) are excluded from this check.
     *
     * @throws AssertionError if any test stubs remain
     */
    public void assertNoTestStubsRemaining() {
        List<StubMapping> remainingTestStubs = getStubMappings().stream()
            .filter(stub -> !permanentStubIds.contains(stub.getId()))
            .toList();

        if (!remainingTestStubs.isEmpty()) {
            String details = remainingTestStubs.stream()
                .map(s -> "  - " + s.getName() + " (" + s.getRequest().getUrl() + ")")
                .collect(Collectors.joining("\n"));
            fail("Test left %d stubs uncleaned:\n%s".formatted(remainingTestStubs.size(), details));
        }
    }

    /**
     * Removes all test stubs, leaving only permanent stubs.
     * This ensures a clean slate for the next test.
     */
    public void cleanupTestStubs() {
        List<StubMapping> testStubs = getStubMappings().stream()
            .filter(stub -> !permanentStubIds.contains(stub.getId()))
            .toList();

        for (StubMapping stub : testStubs) {
            removeStubMapping(stub);
        }

        if (!testStubs.isEmpty()) {
            logger.debug("Cleaned up {} test stubs", testStubs.size());
        }
    }

    /**
     * Asserts that no unhandled requests were made during the test.
     * All requests should be matched by a stub.
     *
     * @throws AssertionError if any requests were not matched
     */
    public void assertNoUnhandledRequests() {
        var unmatchedRequests = findAllUnmatchedRequests();

        if (!unmatchedRequests.isEmpty()) {
            String details = unmatchedRequests.stream()
                .map(req -> "  - " + req.getMethod().getName() + " " + req.getUrl())
                .collect(Collectors.joining("\n"));
            fail("Test had %d unhandled requests:\n%s".formatted(unmatchedRequests.size(), details));
        }
    }

    /**
     * Clears the request journal to prevent interference between tests.
     */
    public void clearRequestJournal() {
        resetRequests();

        logger.debug("Cleared request journal");
    }

    public void stubAuthTest(String description) {
        givenThat(post("/api/auth.test")
            .withName(description)
            .willReturn(okJson(new StringSubstitutor(Map.of(
                "url", config.serverUrl(),
                "team", config.team(),
                "teamId", config.teamId(),
                "userId", config.supportBotUserId(),
                "botId", config.supportBotId()
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

    public Stub stubConversationsOpen(String description, String expectedUserId) {
        String channelId = "D" + expectedUserId;
        StubMapping stubMapping = givenThat(post("/api/conversations.open")
            .withName(description)
            .withFormParam("users", equalTo(expectedUserId))
            .willReturn(okJson("""
                {
                  "ok": true,
                  "channel": {
                    "id": "%s"
                  }
                }""".formatted(channelId)))
        );
        return Stub.builder()
            .mapping(stubMapping)
            .wireMockServer(this)
            .description(description)
            .build();
    }

    public Stub stubChatPostMessage(String description, String expectedChannelId) {
        StubMapping stubMapping = givenThat(post("/api/chat.postMessage")
            .withName(description)
            .withFormParam("channel", equalTo(expectedChannelId))
            .willReturn(okJson("""
                {
                  "ok": true,
                  "channel": "%s",
                  "ts": "1234567890.123456",
                  "message": {
                    "type": "message",
                    "text": "UNSET_BY_TESTS"
                  }
                }""".formatted(expectedChannelId)))
        );
        return Stub.builder()
            .mapping(stubMapping)
            .wireMockServer(this)
            .description(description)
            .build();
    }

    public Stub stubReactionAdd(ReactionAddedExpectation expectation) {
        StubMapping stubMapping = givenThat(post("/api/reactions.add")
            .withName(expectation.description())
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
            .description(expectation.description())
            .build();
    }

    public Stub stubReactionRemove(ReactionAddedExpectation expectation) {
        StubMapping stubMapping = givenThat(post("/api/reactions.remove")
            .withName(expectation.description())
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
            .description(expectation.description())
            .build();
    }

    public <T> StubWithResult<T> stubMessagePosted(ThreadMessagePostedExpectation<T> expectation) {
        StubMapping mapping = givenThat(expectation.receiver().configureStub(post("/api/chat.postMessage"))
            .withName(expectation.description())
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
            .description(expectation.description())
            .build();
    }

    public <T> StubWithResult<T> stubMessageUpdated(MessageUpdatedExpectation<T> expectation) {
        StubMapping stubMapping = givenThat(expectation.receiver().configureStub(post("/api/chat.update"))
            .withName(expectation.description())
            .withFormParam("channel", equalTo(expectation.channelId()))
            .withFormParam("ts", equalTo(expectation.ts().toString()))
            .willReturn(aResponse()
                .withTransformers("response-template")
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(StringSubstitutor.replace("""
                    {{formData request.body 'formArgs' urlDecode=true}}
                    {
                      "ok": true,
                      "channel": "{{formArgs.channel}}",
                      "ts": "{{formArgs.ts}}",
                      "message": {
                        "user": "UNSET_BY_TESTS",
                        "edited": {
                          "user": "UNSET_BY_TESTS",
                          "ts": "UNSET_BY_TESTS"
                        },
                        "bot_id": "UNSET_BY_TESTS",
                        "app_id": "UNSET_BY_TESTS",
                        "team": "UNSET_BY_TESTS",
                        "type": "message",
                        "ts": "{{formArgs.ts}}",
                        "thread_ts": "${thread_ts}",
                        "attachments": {{formArgs.attachments}},
                        "blocks": {{formArgs.blocks}}
                      }
                    }
                    """, Map.of(
                        "thread_ts", expectation.threadTs().toString()
                    )))));
        return StubWithResult.<T>builder()
            .mapping(stubMapping)
            .wireMockServer(this)
            .receiver(expectation.receiver())
            .description(expectation.description())
            .build();
    }

    public Stub stubGetPermalink(String description, String channelId, MessageTs ts, String permalink) {
        StubMapping stubMapping = givenThat(post("/api/chat.getPermalink")
            .withName(description)
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
            .description(description)
            .build();
    }

    public Stub stubGetPermalink(String description, String channelId, MessageTs ts) {
        return stubGetPermalink(description, channelId, ts, "https://slack.com/messages/" + channelId + "/" + ts);
    }

    public Stub stubGetMessage(MessageToGet message) {
        String responseJson = StringSubstitutor.replace("""
            {
                "ok": true,
                "oldest": "${ts}",
                "messages": [
                    {
                        "text": "${text}",
                        "user": ${userId},
                        "bot_id": ${botId},
                        "team": "${team}",
                        "type": "message",
                        "ts": "${ts}",
                        "thread_ts": "${threadTs}",
                        "blocks": ${blocks}
                    }
                ]
            }
            """, Map.of(
            "userId", message.userId() != null
                ? "\"" + message.userId() + "\""
                : "null",
            "botId", message.botId() != null
                ? "\"" + message.botId() + "\""
                : "null",
            "text", message.text().replace("\"", "\\\""),
            "team", config.team(),
            "blocks", message.blocksJson(),
            "ts", message.ts(),
            "threadTs", message.threadTs()
        ));
        StubMapping stubMapping = givenThat(post("/api/conversations.history")
            .withName(message.description())
            .withFormParam("channel", equalTo(message.channelId()))
            .withFormParam("limit", equalTo("1"))
            .withFormParam("oldest", equalTo(message.ts().toString()))
            .withFormParam("inclusive", equalTo("1"))
            .withFormParam("include_all_metadata", equalTo("0"))
            .willReturn(okJson(responseJson))
        );
        return Stub.builder()
            .mapping(stubMapping)
            .wireMockServer(this)
            .description(message.description())
            .build();
    }

    /**
     * Stub the conversations.replies API to return message info with thread context.
     * This is used to check if a message is a thread reply.
     */
    public Stub stubConversationsReplies(ConversationRepliesToGet conversationReplies) {
        String messages;
        // Slack doesn't set thread_ts in case it's a single message in the thread,
        // But in case there are multiple messages, it sets it for all of them,
        if (conversationReplies.reply() != null) {
            assertThat(conversationReplies.threadTs()).isNotNull();
            messages = StringSubstitutor.replace("""
                [
                    {
                        "type": "message",
                        "ts": "${ts}",
                        "thread_ts": ${threadTsValue}
                    },
                    {
                        "type": "message",
                        "ts": "${replyTs}",
                        "thread_ts": ${threadTsValue}
                    }
                ]
                """, Map.of(
                    "ts", conversationReplies.ts().toString(),
                    "threadTsValue", conversationReplies.threadTs().toString(),
                    "replyTs", conversationReplies.reply().toString()
            ));
        } else {
            messages = StringSubstitutor.replace("""
                [
                    {
                        "type": "message",
                        "ts": "${ts}",
                        "thread_ts": null
                    }
                ]
                """, Map.of(
                "ts", conversationReplies.ts().toString()
            ));
        }
        StubMapping stubMapping = givenThat(post("/api/conversations.replies")
            .withName(conversationReplies.description())
            .withFormParam("channel", equalTo(conversationReplies.channelId()))
            .withFormParam("ts", equalTo(conversationReplies.ts().toString()))
            .withFormParam("limit", equalTo("1"))
            .willReturn(okJson(StringSubstitutor.replace("""
                    {
                        "ok": true,
                        "messages": ${messages}
                    }
                    """, Map.of(
                        "messages", messages
                    )))
        ));
        return Stub.builder()
            .mapping(stubMapping)
            .wireMockServer(this)
            .description(conversationReplies.description())
            .build();
    }

    public Stub stubGetUserProfileById(UserProfileToGet userProfile) {
        StubMapping stubMapping = givenThat(post("/api/users.info")
            .withName(userProfile.description())
            .withFormParam("user", equalTo(userProfile.userId()))
            .willReturn(okJson(StringSubstitutor.replace("""
                {
                    "ok": true,
                    "user": {
                        "id": "${userId}",
                        "is_bot": false,
                        "profile": {
                            "email": "${email}"
                        }
                    }
                }
                """, Map.of(
                    "userId", userProfile.userId(),
                    "email", userProfile.email()
                )))
        ));
        return Stub.builder()
            .mapping(stubMapping)
            .wireMockServer(this)
            .description(userProfile.description())
            .build();
    }

    public <T> StubWithResult<T> stubEphemeralMessagePosted(EphemeralMessageExpectation<T> expectation) {
        StubMapping stubMapping = givenThat(expectation.receiver().configureStub(post("/api/chat.postEphemeral"))
            .withName(expectation.description())
            .withFormParam("channel", equalTo(expectation.channelId()))
            .withFormParam("thread_ts", equalTo(expectation.threadTs().toString()))
            .withFormParam("user", equalTo(expectation.userId()))
            .willReturn(okJson("""
                {"ok": true, "message_ts": "1234567890.123456"}
                """))
        );
        return StubWithResult.<T>builder()
            .mapping(stubMapping)
            .wireMockServer(this)
            .receiver(expectation.receiver())
            .description(expectation.description())
            .build();
    }

    public <T> StubWithResult<T> stubViewsOpen(ViewsOpenExpectation<T> expectation) {
        StubMapping mapping = givenThat(expectation.receiver().configureStub(post("/api/views.open"))
            .withName(expectation.description())
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
                        "callback_id": {{toJson formArgView.callback_id}},
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
            .description(expectation.description())
            .build();
    }

    /**
     * Returns a DSL for setting up permanent stubs for NFT.
     * Permanent stubs use response templating and are not cleaned up between iterations.
     */
    public PermanentStubs permanent() {
        return new PermanentStubs();
    }

    /**
     * Inner class providing DSL for permanent NFT stubs.
     * These stubs use response templating to echo back IDs and add realistic latency.
     * Latency values are based on actual Slack API measurements.
     */
    public class PermanentStubs {
        /**
         * Stub chat.postMessage for ticket form creation.
         * Uses response templating to echo back channel, thread_ts, and generate a new ts.
         * Latency: ~325ms (based on actual Slack API measurements).
         */
        public void stubTicketFormPosted() {
            givenThat(post("/api/chat.postMessage")
                .withName("permanent-ticket-form-posted")
                // Match any channel - permanent stubs are broad
                .willReturn(aResponse()
                    .withTransformers("response-template")
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withUniformRandomDelay(300, 350)
                    .withBody("""
                        {{formData request.body 'formArgs' urlDecode=true}}
                        {{#assign 'generatedTs'}}{{messageTs}}{{/assign}}
                        {
                          "ok": true,
                          "channel": "{{formArgs.channel}}",
                          "ts": "{{generatedTs}}",
                          "message": {
                            "user": "UNSET_BY_TESTS",
                            "bot_id": "UNSET_BY_TESTS",
                            "app_id": "UNSET_BY_TESTS",
                            "team": "UNSET_BY_TESTS",
                            "type": "message",
                            "ts": "{{generatedTs}}",
                            "thread_ts": "{{formArgs.thread_ts}}",
                            "text": "{{formArgs.text}}",
                            "blocks": {{formArgs.blocks}}
                          }
                        }
                        """)));
            logger.info("Set up permanent stub for chat.postMessage (ticket form)");
        }

        /**
         * Stub chat.update for ticket form updates (close/reopen).
         * Uses response templating.
         * Latency: ~350ms (based on actual Slack API measurements).
         */
        public void stubMessageUpdated() {
            givenThat(post("/api/chat.update")
                .withName("permanent-message-updated")
                .willReturn(aResponse()
                    .withTransformers("response-template")
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withUniformRandomDelay(300, 400)
                    .withBody("""
                        {{formData request.body 'formArgs' urlDecode=true}}
                        {
                          "ok": true,
                          "channel": "{{formArgs.channel}}",
                          "ts": "{{formArgs.ts}}",
                          "message": {
                            "user": "UNSET_BY_TESTS",
                            "bot_id": "UNSET_BY_TESTS",
                            "app_id": "UNSET_BY_TESTS",
                            "team": "UNSET_BY_TESTS",
                            "type": "message",
                            "ts": "{{formArgs.ts}}",
                            "blocks": {{formArgs.blocks}}
                          }
                        }
                        """)));
            logger.info("Set up permanent stub for chat.update");
        }

        /**
         * Stub views.open for summary modal.
         * Uses response templating to echo back view content.
         * Latency: ~100ms (based on actual Slack API measurements).
         */
        public void stubViewsOpen() {
            givenThat(post("/api/views.open")
                .withName("permanent-views-open")
                .willReturn(aResponse()
                    .withTransformers("response-template")
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withUniformRandomDelay(80, 120)
                    .withBody("""
                        {{formData request.body 'formArgs' urlDecode=true}}
                        {{parseJson formArgs.view 'formArgView'}}
                        {
                          "ok": true,
                          "view": {
                            "id": "V{{randomInt lower=1000000000 upper=9999999999}}",
                            "team_id": "UNSET_BY_TESTS",
                            "root_view_id": "UNSET_BY_TESTS",
                            "app_id": "UNSET_BY_TESTS",
                            "external_id": "",
                            "app_installed_team_id": "UNSET_BY_TESTS",
                            "bot_id": "UNSET_BY_TESTS",
                            "hash": "UNSET_BY_TESTS",
                            "type": "modal",
                            "callback_id": {{toJson formArgView.callback_id}},
                            "blocks": {{toJson formArgView.blocks}},
                            "private_metadata": {{toJson formArgView.private_metadata}},
                            "title": {{toJson formArgView.title}},
                            "close": {{toJson formArgView.close}},
                            "submit": {{toJson formArgView.submit}}
                          }
                        }
                        """)));
            logger.info("Set up permanent stub for views.open");
        }

        /**
         * Stub reactions.add for checkmark.
         * Latency: ~125ms (based on actual Slack API measurements).
         */
        public void stubReactionAdd() {
            givenThat(post("/api/reactions.add")
                .withName("permanent-reaction-add")
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withUniformRandomDelay(100, 150)
                    .withBody("""
                        {"ok": true}
                        """)));
            logger.info("Set up permanent stub for reactions.add");
        }

        /**
         * Stub chat.getPermalink.
         * Latency: ~65ms (based on actual Slack API measurements).
         */
        public void stubGetPermalink() {
            givenThat(post("/api/chat.getPermalink")
                .withName("permanent-get-permalink")
                .willReturn(aResponse()
                    .withTransformers("response-template")
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withUniformRandomDelay(50, 80)
                    .withBody("""
                        {{formData request.body 'formArgs' urlDecode=true}}
                        {
                          "ok": true,
                          "channel": "{{formArgs.channel}}",
                          "permalink": "https://slack.com/messages/{{formArgs.channel}}/{{formArgs.message_ts}}"
                        }
                        """)));
            logger.info("Set up permanent stub for chat.getPermalink");
        }

        /**
         * Stub conversations.history for message fetch.
         * Latency: ~125ms (based on actual Slack API measurements).
         */
        public void stubGetMessage() {
            givenThat(post("/api/conversations.history")
                .withName("permanent-get-message")
                .willReturn(aResponse()
                    .withTransformers("response-template")
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withUniformRandomDelay(100, 150)
                    .withBody(StringSubstitutor.replace("""
                        {{formData request.body 'formArgs' urlDecode=true}}
                        {
                          "ok": true,
                          "oldest": "{{formArgs.oldest}}",
                          "messages": [
                            {
                              "text": "NFT test message",
                              "user": "UNFT_USER",
                              "team": "${team}",
                              "type": "message",
                              "ts": "{{formArgs.oldest}}",
                              "thread_ts": "{{formArgs.oldest}}",
                              "blocks": []
                            }
                          ]
                        }
                        """, Map.of(
                            "team", config.team()
                        )))));
            logger.info("Set up permanent stub for conversations.history");
        }

        /**
         * Stub conversations.replies for thread context.
         * Latency: ~125ms (similar to conversations.history).
         */
        public void stubConversationsReplies() {
            givenThat(post("/api/conversations.replies")
                .withName("permanent-conversations-replies")
                .willReturn(aResponse()
                    .withTransformers("response-template")
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withUniformRandomDelay(100, 150)
                    .withBody("""
                        {{formData request.body 'formArgs' urlDecode=true}}
                        {
                          "ok": true,
                          "messages": [
                            {
                              "type": "message",
                              "ts": "{{formArgs.ts}}",
                              "thread_ts": null
                            }
                          ]
                        }
                        """)));
            logger.info("Set up permanent stub for conversations.replies");
        }

        /**
         * Stub chat.postEphemeral for rating request.
         * Latency: ~160ms (based on actual Slack API measurements).
         */
        public void stubEphemeralMessage() {
            givenThat(post("/api/chat.postEphemeral")
                .withName("permanent-ephemeral-message")
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withUniformRandomDelay(120, 200)
                    .withBody("""
                        {"ok": true, "message_ts": "1234567890.123456"}
                        """)));
            logger.info("Set up permanent stub for chat.postEphemeral");
        }

        /**
         * Stub users.info for user profile lookup.
         * Latency: ~100ms (estimate similar to views.open).
         */
        public void stubUsersInfo() {
            givenThat(post("/api/users.info")
                .withName("permanent-users-info")
                .willReturn(aResponse()
                    .withTransformers("response-template")
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withUniformRandomDelay(40, 60)
                    .withBody("""
                        {{formData request.body 'formArgs' urlDecode=true}}
                        {
                          "ok": true,
                          "user": {
                            "id": "{{formArgs.user}}",
                            "is_bot": false,
                            "profile": {
                              "email": "nft-user@example.com"
                            }
                          }
                        }
                        """)));
            logger.info("Set up permanent stub for users.info");
        }

        /**
         * Sets up all permanent stubs needed for NFT.
         */
        public void setupAllNftStubs() {
            stubAuthTest("nft-auth-test");
            stubTicketFormPosted();
            stubMessageUpdated();
            stubViewsOpen();
            stubReactionAdd();
            stubGetPermalink();
            stubGetMessage();
            stubConversationsReplies();
            stubEphemeralMessage();
            stubUsersInfo();
            logger.info("Set up all permanent NFT stubs");
        }
    }
}
