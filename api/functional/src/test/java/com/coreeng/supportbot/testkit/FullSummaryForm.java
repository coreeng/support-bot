package com.coreeng.supportbot.testkit;

import java.time.temporal.ChronoUnit;
import static java.util.stream.Collectors.joining;

import org.apache.commons.text.StringSubstitutor;

import com.coreeng.supportbot.Config;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.google.common.collect.ImmutableMap;

import lombok.RequiredArgsConstructor;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

public class FullSummaryForm {
    @RequiredArgsConstructor
    public static class Reciever implements StubWithResult.Receiver<FullSummaryForm> {
        private final static ObjectMapper objectMapper = new ObjectMapper();
        private final Ticket ticket;

        @Override
        public MappingBuilder configureStub(MappingBuilder stubBuilder) {
            return stubBuilder;
        }

        @Override
        public FullSummaryForm assertAndExtractResult(ServeEvent servedStub) throws Exception {
            String rawView = servedStub.getRequest().formParameter("view").getValues().getFirst();
            JsonNode view = objectMapper.readTree(rawView);
            assertThatJson(view.get("title").toString()).isEqualTo(String.format(
                """
                    {
                      "type": "plain_text",
                      "text": "Ticket ID-%d Summary",
                      "emoji": false
                    }
                    """, ticket.id()));
            assertThatJson(view.get("close").toString()).isEqualTo(
                """
                    {
                      "type": "plain_text",
                      "text": "Close",
                      "emoji": false
                    }
                    """
            );
            assertThatJson(view.get("submit").toString()).isEqualTo(
                """
                    {
                      "type": "plain_text",
                      "text": "Apply Changes",
                      "emoji": false
                    }
                    """
            );
            assertThatJson(view.get("private_metadata").asText()).isEqualTo(String.format(
                """
                    {
                      "ticketId": %d
                    }
                    """, ticket.id()));
            ArrayNode expectedBlocks = buildExpectedBlocks();
            JsonNode realBlocks = view.get("blocks");
            assertThatJson(realBlocks.toString()).isEqualTo(expectedBlocks.toString());
            return new FullSummaryForm();
        }

        private ArrayNode buildExpectedBlocks() throws Exception {
            ArrayNode result = objectMapper.createArrayNode();
            ObjectNode header = (ObjectNode) objectMapper.readTree("""
                {
                  "type": "header",
                  "text": {
                    "type": "plain_text",
                    "text": "Ticket Summary"
                  }
                }
                """);
            ArrayNode queryBlocks = (ArrayNode) objectMapper.readTree(ticket.queryBlocksJson());
            String escalationsBlock = buildEscalationBlock();
            String statusHistoryElements = buildStatusHistoryBlocks();

            String teamInitialOption = ticket.team() != null
                ? String.format(
                    """
                        "initial_option": {
                          "text": {"type": "plain_text", "text": "%s"},
                          "value": "%s"
                        },
                    """,
                    ticket.team(), ticket.team()
                )
                : "";

            String tagsInitialOptions = !ticket.tags().isEmpty()
                ? String.format(
                    """
                        "initial_options": [%s],
                    """,
                    buildTagsInitialOptionsFromConfig()
                )
                : "";

            String impactInitialOption = ticket.impact() != null
                ? String.format(
                    """
                        "initial_option": {
                          "text": {"type": "plain_text", "text": "%s"},
                          "value": "%s"
                        },
                    """,
                    findImpactLabel(ticket.impact()), ticket.impact()
                )
                : "";

            ArrayNode restBlocks = (ArrayNode) objectMapper.readTree(StringSubstitutor.replace(
                """
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
                              ${statusHistoryElements}
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
                      ${escalationsBlock},
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
                              "text": "${statusInitialText}"
                            },
                            "value": "${statusInitialValue}"
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
                          ${teamInitialOption}
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
                          ${tagsInitialOptions}
                          "options": ${tagsOptions}
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
                          ${impactInitialOption}
                          "options": ${impactsOptions}
                        }
                      }
                    ]
                    """,
                ImmutableMap.<String, Object>builder()
                    .put("userId", ticket.user().slackUserId())
                    .put("postedAt", ticket.queryTs().instant().truncatedTo(ChronoUnit.MINUTES))
                    .put("postedAtTs", ticket.queryTs().instant().getEpochSecond())
                    .put("statusHistoryElements", statusHistoryElements)
                    .put("statusInitialText", ticket.status().label())
                    .put("statusInitialValue", ticket.status().code())
                    .put("queryPermalink", ticket.queryPermalink())
                    .put("escalationsBlock", escalationsBlock)
                    .put("teamInitialOption", teamInitialOption)
                    .put("tagsInitialOptions", tagsInitialOptions)
                    .put("impactInitialOption", impactInitialOption)
                    .put("tagsOptions", buildTagsOptionsFromConfig())
                    .put("impactsOptions", buildImpactsOptionsFromConfig())
                    .build()
            ));

            result.add(header);
            for (JsonNode n : queryBlocks) {
                result.add(n);
            }
            for (JsonNode n : restBlocks) {
                result.add(n);
            }
            return result;
        }

        private String buildStatusHistoryBlocks() {
            StringBuilder statusHistoryBuilder = new StringBuilder();
            for (int i = 0; i < ticket.logs().size(); i++) {
                Ticket.StatusLog log = ticket.logs().get(i);
                Ticket.Status st = Ticket.Status.fromCode(log.event());
                String tail = (i < ticket.logs().size() - 1) ? "  |\\n" : "";
                statusHistoryBuilder.append(String.format(
                    """
                        {
                          "type": "emoji",
                          "name": "%s"
                        },
                        {
                          "type": "text",
                          "text": " %s: "
                        },
                        {
                          "type": "date",
                          "timestamp": %d,
                          "format": "{date_short_pretty} at {time}",
                          "fallback": "%s"
                        },
                        {
                          "type": "text",
                          "text": "\\n%s"
                        }
                    """,
                    st.emojiName(),
                    st.label(),
                    log.date().getEpochSecond(),
                    log.date().truncatedTo(ChronoUnit.MINUTES),
                    tail
                ));
                if (i < ticket.logs().size() - 1) {
                    statusHistoryBuilder.append(",");
                }
            }
            return statusHistoryBuilder.toString();
        }

        private String buildEscalationBlock() {
            String escalationsBlock;
            if (ticket.escalations().isEmpty()) {
                escalationsBlock = """
                      {
                        "type": "context",
                        "elements": [
                          {
                            "type": "plain_text",
                            "text": "No escalations for this ticket"
                          }
                        ]
                      }
                    """;
            } else {
                String teamName = ticket.escalations().getFirst().team();
                String groupId = ticket.config().escalationTeams().stream()
                    .filter(t -> t.name().equals(teamName))
                    .findFirst()
                    .map(Config.EscalationTeam::slackGroupId)
                    .orElseThrow(() -> new AssertionError("Team not found: " + teamName));
                escalationsBlock = String.format(
                    """
                        {
                          "type": "section",
                          "fields": [
                            {"type": "plain_text", "text": "Status: Opened"},
                            {"type": "mrkdwn", "text": "Team: <!subteam^%s>"}
                          ]
                        }""",
                    groupId
                );
            }
            return escalationsBlock;
        }

        private String buildTagsInitialOptionsFromConfig() {
            return ticket.tags().stream()
                .map(tag -> String.format("""
                        {"text": {"type": "plain_text", "text": "%s"}, "value": "%s"}""",
                    findTagLabel(tag), tag
                ))
                .collect(joining(", "));
        }

        private String buildTagsOptionsFromConfig() {
            return ticket.config().tags().stream()
                .map(tag -> """
                    {"text": {"type": "plain_text", "text": "%s"}, "value": "%s"}""".formatted(tag.label(), tag.code()))
                .collect(joining(",", "[", "]"));
        }

        private String buildImpactsOptionsFromConfig() {
            return ticket.config().impacts().stream()
                .map(imp -> String.format("""
                    {"text": {"type": "plain_text", "text": "%s"}, "value": "%s"}""",
                    imp.label(), imp.code()))
                .collect(joining(",", "[", "]"));
        }

        private String findTagLabel(String code) {
            return ticket.config().tags().stream()
                .filter(t -> t.code().equals(code))
                .findFirst()
                .map(Config.Tag::label)
                .orElse(code);
        }

        private String findImpactLabel(String code) {
            if (code == null) return "Not Evaluated";
            return ticket.config().impacts().stream()
                .filter(i -> i.code().equals(code))
                .findFirst()
                .map(Config.Impact::label)
                .orElse(code);
        }
    }
}
