package com.coreeng.supportbot.testkit;

import com.coreeng.supportbot.Config;
import com.coreeng.supportbot.wiremock.SlackWiremock;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.ContentType;
import lombok.RequiredArgsConstructor;
import org.apache.commons.text.StringSubstitutor;

import java.util.Map;

import static io.restassured.RestAssured.given;

@RequiredArgsConstructor
public class SupportBotSlackClient {
    private final Config config;
    private final SlackWiremock slackWiremock;

    public SlackMessage notifyMessagePosted(MessageToPost message) {
        String ts = message.ts() != null
            ? message.ts()
            : SlackMessage.generateNewTs();
        String body = StringSubstitutor.replace(
            """
                {
                  "token": "${token}",
                  "team_id": "${teamId}",
                  "context_team_id": "${teamId}",
                  "api_app_id": "UNSET_BY_TESTS",
                  "event": {
                    "user": "${userId}",
                    "type": "message",
                    "ts": "${ts}",
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
                "userId", message.userId(),
                "message", message.message(),
                "channelId", message.channelId(),
                "ts", ts
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
            .ts(ts)
            .channelId(message.channelId())
            .slackWiremock(slackWiremock)
            .build();
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
                    "user": "${userId}",
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
                "userId", reaction.userId(),
                "reaction", reaction.reaction(),
                "channelId", reaction.channelId(),
                "ts", reaction.ts()
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
}
