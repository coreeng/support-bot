package com.coreeng.supportbot;

import io.restassured.http.ContentType;
import io.restassured.http.Header;

import java.time.Instant;

import static io.restassured.RestAssured.given;

public class SupportBotSlackClient {
    // TODO: parameterise
    public void notifyChannelMessagePosted() {
        long currentEpochSecond = Instant.now().getEpochSecond();
        given()
            .baseUri("http://localhost:8080/slack/events")
            .when()
            .header(new Header(
                "X-Slack-Request-Timestamp",
                Long.toString(currentEpochSecond)
            ))
            .header(new Header(
                "X-Slack-Signature",
                "ble"
            ))
            .contentType(ContentType.JSON)
            .body("""
                {
                  "token": "VAmyoZLQqHJsE2QvKgY3VqBA",
                  "team_id": "T014GGGRDUK",
                  "context_team_id": "T014GGGRDUK",
                  "api_app_id": "A08C6KMK0JH",
                  "event": {
                    "user": "U069PNNUUBB",
                    "type": "message",
                    "ts": "1751299902.155899",
                    "client_msg_id": "03ede034-b608-4428-baca-926730014431",
                    "text": "asd",
                    "team": "T014GGGRDUK",
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
                                "text": "asd"
                              }
                            ]
                          }
                        ]
                      }
                    ],
                    "channel": "C08CC3MCBKN",
                    "event_ts": "1751299902.155899",
                    "channel_type": "group"
                  },
                  "type": "event_callback",
                  "event_id": "Ev093QB18550",
                  "event_time": 1751299902,
                  "authorizations": [
                    {
                      "team_id": "T014GGGRDUK",
                      "user_id": "U08C2T9M6NA",
                      "is_bot": true,
                      "is_enterprise_install": false
                    }
                  ],
                  "is_ext_shared_channel": false,
                  "event_context": "4-eyJldCI6Im1lc3NhZ2UiLCJ0aWQiOiJUMDE0R0dHUkRVSyIsImFpZCI6IkEwOEM2S01LMEpIIiwiY2lkIjoiQzA4Q0MzTUNCS04ifQ"
                }
                """)
            .post()
            .then()
            .assertThat()
            .statusCode(200)
            .log().all();
    }

    public void notifyEyesOnTheQuery() {
        given()
            .baseUri("http://localhost:8080/slack/events")
            .when()
            .contentType(ContentType.JSON)
            .body("""
                {
                  "token": "VAmyoZLQqHJsE2QvKgY3VqBA",
                  "team_id": "T014GGGRDUK",
                  "context_team_id": "T014GGGRDUK",
                  "api_app_id": "A08C6KMK0JH",
                  "event": {
                    "type": "reaction_added",
                    "user": "U069PNNUUBB",
                    "reaction": "eyes",
                    "item": {
                      "type": "message",
                      "channel": "C08CC3MCBKN",
                      "ts": "1751299902.155899"
                    },
                    "item_user": "U069PNNUUBB",
                    "event_ts": "1751299904.001000"
                  },
                  "type": "event_callback",
                  "event_id": "Ev09436T4SGZ",
                  "event_time": 1751299904,
                  "authorizations": [
                    {
                      "team_id": "T014GGGRDUK",
                      "user_id": "U08C2T9M6NA",
                      "is_bot": true,
                      "is_enterprise_install": false
                    }
                  ],
                  "is_ext_shared_channel": false,
                  "event_context": "4-eyJldCI6InJlYWN0aW9uX2FkZGVkIiwidGlkIjoiVDAxNEdHR1JEVUsiLCJhaWQiOiJBMDhDNktNSzBKSCIsImNpZCI6IkMwOENDM01DQktOIn0"
                }
                """)
            .post()
            .then()
            .assertThat()
            .statusCode(200)
            .log().all();
    }
}
