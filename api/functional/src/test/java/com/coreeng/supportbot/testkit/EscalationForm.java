package com.coreeng.supportbot.testkit;

import com.coreeng.supportbot.Config;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;

import lombok.RequiredArgsConstructor;
import org.apache.commons.text.StringSubstitutor;
import java.util.Map;
import java.util.stream.Collectors;

public class EscalationForm {
    @RequiredArgsConstructor
    public static class Receiver implements StubWithResult.Receiver<EscalationForm> {
        private final static ObjectMapper objectMapper = new ObjectMapper();

        private final Ticket ticket;

        @Override
        public MappingBuilder configureStub(MappingBuilder stubBuilder) {
            return stubBuilder;
        }

        @Override
        public EscalationForm assertAndExtractResult(ServeEvent servedStub) throws Exception {
            String rawView = servedStub.getRequest().formParameter("view").getValues().getFirst();
            JsonNode view = objectMapper.readTree(rawView);

            assertThatJson(view.get("title").toString()).isEqualTo(String.format("""
                {
                  "type": "plain_text",
                  "text": "Escalate Ticket ID-%d",
                  "emoji": false
                }
                """, ticket.id()));
            assertThatJson(view.get("close").toString()).isEqualTo("""
                {
                  "type": "plain_text",
                  "text": "Cancel",
                  "emoji": false
                }
                """);
            assertThatJson(view.get("submit").toString()).isEqualTo("""
                {
                  "type": "plain_text",
                  "text": "Escalate",
                  "emoji": false
                }
                """);

            assertThatJson(view.get("private_metadata").asText()).isEqualTo(String.format("""
                {
                  "ticketId": %d
                }
                """, ticket.id()));

            JsonNode realBlocks = view.get("blocks");
            ArrayNode expectedBlocks = buildExpectedBlocks();
            assertThatJson(realBlocks.toString()).isEqualTo(expectedBlocks.toString());

            return new EscalationForm();
        }

        private ArrayNode buildExpectedBlocks() throws Exception {
            String tagOptions = ticket.config().tags().stream()
                .map(tag -> String.format(
                    """
                        {
                          "text": {"type": "plain_text", "text": %s},
                          "value": %s
                        }""",
                    safeJson(tag.label()),
                    safeJson(tag.code())
                ))
                .collect(Collectors.joining(",\n"));

            String teamOptions = ticket.config().escalationTeams().stream()
                .map(Config.EscalationTeam::name)
                .map(teamName -> String.format(
                    """
                        {
                          "text": {"type": "plain_text", "text": %s},
                          "value": %s
                        }""",
                    safeJson(teamName),
                    safeJson(teamName)
                ))
                .collect(Collectors.joining(",\n"));

            String blocksJson = StringSubstitutor.replace(
                """
                [
                  {
                    "type": "input",
                    "block_id": "escalation-tags",
                    "label": { "type": "plain_text", "text": "Pick tags" },
                    "element": {
                      "type": "multi_static_select",
                      "action_id": "escalation-tags",
                      "options": [ ${tagOptions} ]
                    },
                    "optional": false
                  },
                  {
                    "type": "input",
                    "block_id": "escalation-team",
                    "label": { "type": "plain_text", "text": "Team to escalate to" },
                    "element": {
                      "type": "static_select",
                      "action_id": "escalation-team",
                      "options": [ ${teamOptions} ]
                    },
                    "optional": false
                  }
                ]
                """,
                Map.of(
                    "tagOptions", tagOptions,
                    "teamOptions", teamOptions
                )
            );

            return (ArrayNode) objectMapper.readTree(blocksJson);
        }

        private String safeJson(String value) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}


