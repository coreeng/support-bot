package com.coreeng.supportbot.testkit;

import java.util.Map;
import java.util.stream.Collectors;

import io.restassured.response.ValidatableResponse;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.commons.text.StringSubstitutor;

import com.coreeng.supportbot.Config;

import static io.restassured.RestAssured.given;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.ContentType;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SupportBotSlackClient {
    private final Config config;
    private final SlackWiremock slackWiremock;

    public SlackMessage notifyMessagePosted(MessageToPost message) {
        String threadTsField = message.threadTs() != null
            ? "\"thread_ts\": \"" + message.threadTs() + "\","
            : "";
        String body = StringSubstitutor.replace(
            """
                {
                  "token": "${token}",
                  "team_id": "${teamId}",
                  "context_team_id": "${teamId}",
                  "api_app_id": "UNSET_BY_TESTS",
                  "event": {
                    ${userField}
                    ${botField}
                    "type": "message",
                    "ts": "${ts}",
                    ${threadTsField}
                    "client_msg_id": "03ede034-b608-4428-baca-926730014431",
                    "text": "${message}",
                    "team": "${teamId}",
                    "blocks": [
                      {
                        "type": "rich_text",
                        "block_id": "CWXU3",
                        "elements": [
                          {
                            "type": "rich_text_section",
                            "elements": [
                              {
                                "type": "text",
                                "text": "${message}"
                              }
                            ]
                          }
                        ]
                      }
                    ],
                    "channel": "${channelId}",
                    "event_ts": "UNSET_BY_TESTS",
                    "channel_type": "group"
                  },
                  "type": "event_callback",
                  "event_id": "Ev093QB18550",
                  "event_time": 1751299902,
                  "authorizations": [
                    {
                      "team_id": "${teamId}",
                      "user_id": "${userId}",
                      "is_bot": false,
                      "is_enterprise_install": false
                    }
                  ],
                  "is_ext_shared_channel": false,
                  "event_context": "4-eyJldCI6Im1lc3NhZ2UiLCJ0aWQiOiJUMDE0R0dHUkRVSyIsImFpZCI6IkEwOEM2S01LMEpIIiwiY2lkIjoiQzA4Q0MzTUNCS04ifQ"
                }
                """,
            Map.of(
                "token", config.supportBot().token(),
                "teamId", message.teamId(),
                "userField", message.userId() != null ? "\"user\": \"" + message.userId() + "\"," : "",
                "botField", message.botId() != null ? "\"bot_id\": \"" + message.botId() + "\"," : "",
                "message", message.message(),
                "channelId", message.channelId(),
                "ts", message.ts().toString(),
                "threadTsField", threadTsField
            )
        );
        given()
            .when()
            .contentType(ContentType.JSON)
            .body(body)
            .post(config.supportBot().baseUrl() + "/slack/events")
            .then()
            .log().ifValidationFails(LogDetail.ALL, true)
            .statusCode(200);
        return SlackMessage.builder()
            .ts(message.ts())
            .channelId(message.channelId())
            .slackWiremock(slackWiremock)
            .build();
    }

    public void notifyMessageDeleted(MessageToDelete message) {
        String threadTsField = message.threadTs() != null
            ? "\"thread_ts\": \"" + message.threadTs() + "\","
            : "";
        String body = StringSubstitutor.replace(
            """
                {
                  "token": "${token}",
                  "team_id": "${teamId}",
                  "context_team_id": "${teamId}",
                  "api_app_id": "UNSET_BY_TESTS",
                  "event": {
                    "type": "message",
                    "subtype": "message_deleted",
                    "hidden": true,
                    "deleted_ts": "${deletedTs}",
                    "channel": "${channelId}",
                    "previous_message": {
                      "type": "message",
                      "user": "${userId}",
                      "text": "deleted message",
                      ${threadTsField}
                      "ts": "${deletedTs}"
                    },
                    "event_ts": "UNSET_BY_TESTS",
                    "ts": "${eventTs}",
                    "channel_type": "group"
                  },
                  "type": "event_callback",
                  "event_id": "Ev093QB18551",
                  "event_time": 1751299903,
                  "authorizations": [
                    {
                      "team_id": "${teamId}",
                      "user_id": "${userId}",
                      "is_bot": false,
                      "is_enterprise_install": false
                    }
                  ],
                  "is_ext_shared_channel": false,
                  "event_context": "4-eyJldCI6Im1lc3NhZ2UiLCJ0aWQiOiJUMDE0R0dHUkRVSyIsImFpZCI6IkEwOEM2S01LMEpIIiwiY2lkIjoiQzA4Q0MzTUNCS04ifQ"
                }
                """,
            Map.of(
                "token", config.supportBot().token(),
                "teamId", message.teamId(),
                "userId", message.userId(),
                "channelId", message.channelId(),
                "deletedTs", message.deletedTs().toString(),
                "eventTs", MessageTs.now().toString(),
                "threadTsField", threadTsField
            )
        );
        given()
            .when()
            .contentType(ContentType.JSON)
            .body(body)
            .post(config.supportBot().baseUrl() + "/slack/events")
            .then()
            .log().ifValidationFails(LogDetail.ALL, true)
            .statusCode(200);
    }

    public void notifyReactionAdded(ReactionToAdd reaction) {
        String body = StringSubstitutor.replace(
            """
                {
                  "token": "${token}",
                  "team_id": "${teamId}",
                  "context_team_id": "${teamId}",
                  "api_app_id": "UNSET_BY_TESTS",
                  "event": {
                    ${userField}
                    ${botField}
                    "type": "reaction_added",
                    "reaction": "${reaction}",
                    "item": {
                      "type": "message",
                      "channel": "${channelId}",
                      "ts": "${ts}"
                    },
                    "item_user": "${userId}",
                    "event_ts": "UNSET_BY_TESTS"
                  },
                  "type": "event_callback",
                  "event_id": "Ev093QB18550",
                  "event_time": 1751299902,
                  "authorizations": [
                    {
                      "team_id": "${teamId}",
                      "user_id": "${userId}",
                      "is_bot": false,
                      "is_enterprise_install": false
                    }
                  ],
                  "is_ext_shared_channel": false,
                  "event_context": "4-eyJldCI6Im1lc3NhZ2UiLCJ0aWQiOiJUMDE0R0dHUkRVSyIsImFpZCI6IkEwOEM2S01LMEpIIiwiY2lkIjoiQzA4Q0MzTUNCS04ifQ"
                }
                """,
            Map.of(
                "token", config.supportBot().token(),
                "teamId", reaction.teamId(),
                "userField", reaction.userId() != null ? "\"user\": \"" + reaction.userId() + "\"," : "",
                "botField", reaction.botId() != null ? "\"bot_id\": \"" + reaction.botId() + "\"," : "",
                "reaction", reaction.reaction(),
                "channelId", reaction.channelId(),
                "ts", reaction.ts().toString()
            )
        );
        given()
            .when()
            .contentType(ContentType.JSON)
            .body(body)
            .post(config.supportBot().baseUrl() + "/slack/events")
            .then()
            .log().ifValidationFails(LogDetail.ALL, true)
            .statusCode(200);
    }

    public void notifyButtonClicked(RawButtonClick click) {
        String payload = StringSubstitutor.replace(
            """
                {
                  "type": "block_actions",
                  "user": {
                    "id": "${userId}",
                    "team_id": "${teamId}"
                  },
                  "token": "${token}",
                  "trigger_id": "${triggerId}",
                  "actions": [
                    {
                      "action_id": "${actionId}",
                      "value": "${privateMetadata}",
                      "type": "button"
                    }
                  ]
                }
                """,
            Map.of(
                "token", config.supportBot().token(),
                "teamId", click.teamId(),
                "userId", click.userId(),
                "actionId", click.actionId(),
                "triggerId", click.triggerId(),
                "privateMetadata", StringEscapeUtils.escapeJson(click.privateMetadata().trim())
            )
        );
        given()
            .when()
            .formParam("payload", payload)
            .post(config.supportBot().baseUrl() + "/slack/events")
            .then()
            .log().ifValidationFails(LogDetail.ALL, true)
            .statusCode(200);
    }

    public void notifyViewSubmitted(RawViewSubmission rawViewSubmission) {
        String payload = createViewSubmittedPayload(rawViewSubmission);
        given()
            .when()
            .formParam("payload", payload)
            .post(config.supportBot().baseUrl() + "/slack/events")
            .then()
            .log().ifValidationFails(LogDetail.ALL, true)
            .statusCode(200);
    }

    public String notifyViewSubmittedAndReturnBody(RawViewSubmission rawViewSubmission) {
        String payload = createViewSubmittedPayload(rawViewSubmission);
        return given()
            .when()
            .formParam("payload", payload)
            .post(config.supportBot().baseUrl() + "/slack/events")
            .then()
            .log().ifValidationFails(LogDetail.ALL, true)
            .statusCode(200)
            .extract().asString();
    }

    private String createViewSubmittedPayload(RawViewSubmission rawViewSubmission) {
        String valuesJson = rawViewSubmission.values().stream()
            .map(value -> String.format("""
                "%s": {
                    "%s": %s
                }
                """, RandomStringUtils.secure().nextAlphabetic(5), value.name(), value.renderJson())
            )
            .collect(Collectors.joining(","));
        return StringSubstitutor.replace("""
            {
                "type": "view_submission",
                "user": {
                  "id": "${userId}",
                  "team_id": "${teamId}"
                },
                "token": "${token}",
                "trigger_id": "${triggerId}",
                "view": {
                  "team_id": "${teamId}",
                  "private_metadata": "${privateMetadata}",
                  "callback_id": "${callbackId}",
                  "type": "${viewType}",
                  "state": {
                    "values": {
                        ${valuesJson}
                    }
                  }
                }
            }
            """,
            Map.of(
                "token", config.supportBot().token(),
                "teamId", rawViewSubmission.teamId(),
                "userId", rawViewSubmission.userId(),
                "triggerId", rawViewSubmission.triggerId(),
                "privateMetadata", StringEscapeUtils.escapeJson(rawViewSubmission.privateMetadata().trim()),
                "callbackId", rawViewSubmission.callbackId(),
                "viewType", rawViewSubmission.viewType(),
                "valuesJson", valuesJson
            )
        );
    }

    public ValidatableResponse notifyBlockSuggestion(RawBlockSuggestion blockSuggestion) {
        String payload = StringSubstitutor.replace(
            """
                {
                  "type": "block_suggestion",
                  "user": {
                    "id": "${userId}",
                    "team_id": "${teamId}"
                  },
                  "token": "${token}",
                  "action_id": "${actionId}",
                  "value": "${value}",
                  "view": {
                    "id": "VIEW_ID",
                    "team_id": "${teamId}",
                    "type": "${viewType}",
                    "private_metadata": "${privateMetadata}",
                    "callback_id": "${callbackId}"
                  }
                }
                """,
            Map.of(
                "token", config.supportBot().token(),
                "teamId", blockSuggestion.teamId(),
                "userId", blockSuggestion.userId(),
                "actionId", blockSuggestion.actionId(),
                "value", blockSuggestion.value(),
                "viewType", blockSuggestion.viewType(),
                "privateMetadata", StringEscapeUtils.escapeJson(blockSuggestion.privateMetadata().trim()),
                "callbackId", blockSuggestion.callbackId()
            )
        );
        return given()
            .when()
            .formParam("payload", payload)
            .post(config.supportBot().baseUrl() + "/slack/events")
            .then()
            .log().ifValidationFails(LogDetail.ALL, true);
    }
}
