package com.coreeng.supportbot.testkit;

import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.apache.commons.text.StringSubstitutor;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;

import lombok.RequiredArgsConstructor;

public class FullSummaryForm {
    @RequiredArgsConstructor
    public static class Reciever implements StubWithResult.Receiver<FullSummaryForm> {
        private final Ticket ticket;

        @Override
        public MappingBuilder configureStub(MappingBuilder stubBuilder) {
            return stubBuilder;
        }

        @Override
        public FullSummaryForm assertAndExtractResult(ServeEvent servedStub) throws Exception {
            String rawView = servedStub.getRequest().formParameter("view").getValues().getFirst();
            JSONObject view = new JSONObject(rawView);
            JSONAssert.assertEquals(String.format("""
                    {
                        "type": "plain_text",
                        "text": "Ticket ID-%d Summary",
                        "emoji": false
                    }
                    """, ticket.id()),
                view.getJSONObject("title"),
                JSONCompareMode.STRICT_ORDER
            );
            JSONAssert.assertEquals("""
                    {
                        "type": "plain_text",
                        "text": "Close",
                        "emoji": false
                    }
                    """,
                view.getJSONObject("close"),
                JSONCompareMode.STRICT_ORDER
            );
            JSONAssert.assertEquals("""
                    {
                        "type": "plain_text",
                        "text": "Apply Changes",
                        "emoji": false
                    }
                    """,
                view.getJSONObject("submit"),
                JSONCompareMode.STRICT_ORDER
            );
            JSONAssert.assertEquals(String.format("""
                    {
                      "ticketId": %d
                    }
                    """, ticket.id()),
                view.getString("private_metadata"),
                JSONCompareMode.STRICT_ORDER
            );
            JSONArray expectedBlocks = buildExpectedBlocks();
            JSONArray realBlocks = view.getJSONArray("blocks");
            JSONAssert.assertEquals(expectedBlocks, realBlocks, JSONCompareMode.STRICT_ORDER);
            return new FullSummaryForm();
        }

        private JSONArray buildExpectedBlocks() throws JSONException {
            JSONArray result = new JSONArray();
            JSONObject header = new JSONObject("""
                    {
                      "type": "header",
                      "text": {
                        "type": "plain_text",
                        "text": "Ticket Summary"
                      }
                    }
                """);
            JSONArray queryBlocks = new JSONArray(ticket.queryBlocksJson());
            JSONArray restBlocks = new JSONArray(StringSubstitutor.replace("""
                [
                  {
                    "type": "context",
                    "elements": [
                      {
                        "type": "mrkdwn",
                        "text": "Sent by <@${userId}> | <!date^${postedAtTs}^{date_short_pretty} at {time}|${postedAt}> | <${queryPermalink}|View Message>\\n",
                        "verbatim": false
                      }
                    ]
                  },
                  {
                    "type": "divider"
                  },
                  {
                    "type": "header",
                    "text": {
                      "type": "plain_text",
                      "text": "Status History"
                    }
                  },
                  {
                    "type": "rich_text",
                    "elements": [
                      {
                        "type": "rich_text_section",
                        "elements": [
                          {
                            "type": "emoji",
                            "name": "large_orange_circle"
                          },
                          {
                            "type": "text",
                            "text": " Opened: "
                          },
                          {
                            "type": "date",
                            "timestamp": ${openedAtTs},
                            "format": "{date_short_pretty} at {time}",
                            "fallback": "${openedAt}"
                          },
                          {
                            "type": "text",
                            "text": "\\n"
                          }
                        ]
                      }
                    ]
                  },
                  {
                    "type": "divider"
                  },
                  {
                    "type": "header",
                    "text": {
                      "type": "plain_text",
                      "text": "Escalations"
                    }
                  },
                  {
                    "type": "context",
                    "elements": [
                      {
                        "type": "plain_text",
                        "text": "No escalations for this ticket"
                      }
                    ]
                  },
                  {
                    "type": "divider"
                  },
                  {
                    "type": "header",
                    "text": {
                      "type": "plain_text",
                      "text": "Modify Ticket"
                    }
                  },
                  {
                    "type": "input",
                    "label": {
                      "type": "plain_text",
                      "text": "Change Status"
                    },
                    "optional": false,
                    "element": {
                      "type": "static_select",
                      "action_id": "change-status",
                      "initial_option": {
                        "text": {
                          "type": "plain_text",
                          "text": "Opened"
                        },
                        "value": "opened"
                      },
                      "options": [
                        {
                          "text": {
                            "type": "plain_text",
                            "text": "Opened"
                          },
                          "value": "opened"
                        },
                        {
                          "text": {
                            "type": "plain_text",
                            "text": "Closed"
                          },
                          "value": "closed"
                        }
                      ]
                    }
                  },
                  {
                    "type": "input",
                    "label": {
                      "type": "plain_text",
                      "text": "Select the Author's Team"
                    },
                    "optional": false,
                    "element": {
                      "type": "static_select",
                      "action_id": "change-team",
                      "option_groups": [
                        {
                          "options": [{
                            "text": {
                              "text": "wow",
                              "type": "plain_text"
                            },
                            "value": "wow"
                          }],
                          "label": {
                            "text": "Suggested teams",
                            "type": "plain_text"
                          }
                        },
                        {
                          "label": {
                            "type": "plain_text",
                            "text": "Others"
                          },
                          "options": [
                            {
                              "text": {
                                "type": "plain_text",
                                "text": "connected-app"
                              },
                              "value": "connected-app"
                            },
                            {
                              "text": {
                                "type": "plain_text",
                                "text": "infra-integration"
                              },
                              "value": "infra-integration"
                            }
                          ]
                        }
                      ]
                    }
                  },
                  {
                    "type": "input",
                    "label": {
                      "type": "plain_text",
                      "text": "Select Tags"
                    },
                    "hint": {
                      "type": "plain_text",
                      "text": "Select all applicable tags."
                    },
                    "optional": false,
                    "element": {
                      "type": "multi_static_select",
                      "action_id": "change-tags",
                      "options": [
                        {
                          "text": {
                            "type": "plain_text",
                            "text": "Ingresses"
                          },
                          "value": "ingresses"
                        },
                        {
                          "text": {
                            "type": "plain_text",
                            "text": "Jenkins Pipelines"
                          },
                          "value": "jenkins-pipelines"
                        },
                        {
                          "text": {
                            "type": "plain_text",
                            "text": "Networking"
                          },
                          "value": "networking"
                        },
                        {
                          "text": {
                            "type": "plain_text",
                            "text": "Vault"
                          },
                          "value": "vault"
                        },
                        {
                          "text": {
                            "type": "plain_text",
                            "text": "Persistence/Brokers"
                          },
                          "value": "persistence-brokers"
                        },
                        {
                          "text": {
                            "type": "plain_text",
                            "text": "Observability"
                          },
                          "value": "observability"
                        },
                        {
                          "text": {
                            "type": "plain_text",
                            "text": "DNS"
                          },
                          "value": "dns"
                        },
                        {
                          "text": {
                            "type": "plain_text",
                            "text": "Osprey"
                          },
                          "value": "osprey"
                        }
                      ]
                    }
                  },
                  {
                    "type": "input",
                    "label": {
                      "type": "plain_text",
                      "text": "Change Impact"
                    },
                    "optional": false,
                    "element": {
                      "type": "static_select",
                      "action_id": "change-impact",
                      "placeholder": {
                        "type": "plain_text",
                        "text": "Not Evaluated"
                      },
                      "options": [
                        {
                          "text": {
                            "type": "plain_text",
                            "text": "Production Blocking"
                          },
                          "value": "productionBlocking"
                        },
                        {
                          "text": {
                            "type": "plain_text",
                            "text": "BAU Blocking"
                          },
                          "value": "bauBlocking"
                        },
                        {
                          "text": {
                            "type": "plain_text",
                            "text": "Abnormal Behaviour"
                          },
                          "value": "abnormalBehaviour"
                        }
                      ]
                    }
                  }
                ]
                """, Map.of(
                "userId", ticket.user().slackUserId(),
                "postedAt", ticket.queryTs().instant().truncatedTo(ChronoUnit.MINUTES),
                "postedAtTs", ticket.queryTs().instant().getEpochSecond(),
                "openedAt", ticket.logs().getFirst().date().truncatedTo(ChronoUnit.MINUTES),
                "openedAtTs", ticket.logs().getFirst().date().getEpochSecond(),
                "queryPermalink", ticket.queryPermalink()
            )));

            result.put(header);
            for (int i = 0; i < queryBlocks.length(); i++) {
                result.put(queryBlocks.get(i));
            }
            for (int i = 0; i < restBlocks.length(); i++) {
                result.put(restBlocks.get(i));
            }
            return result;
        }
    }
}
