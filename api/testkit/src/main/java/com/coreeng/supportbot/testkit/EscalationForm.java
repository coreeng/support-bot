package com.coreeng.supportbot.testkit;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.text.StringSubstitutor;

public class EscalationForm {
    @RequiredArgsConstructor
    public static class Receiver implements StubWithResult.Receiver<EscalationForm> {
        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

        private final Ticket ticket;

        @Override
        public MappingBuilder configureStub(MappingBuilder stubBuilder) {
            return stubBuilder;
        }

        @Override
        public EscalationForm assertAndExtractResult(ServeEvent servedStub) {
            String rawView =
                    servedStub.getRequest().formParameter("view").getValues().getFirst();
            String expectedView = buildExpectedViewJson();
            assertThatJson(rawView).isEqualTo(expectedView);
            return new EscalationForm();
        }

        private String buildExpectedViewJson() {
            String tagOptions = ticket.config().tags().stream()
                    .map(tag -> String.format("""
                        {
                          "text": {"type": "plain_text", "text": %s},
                          "value": %s
                        }""", safeJson(tag.label()), safeJson(tag.code())))
                    .collect(Collectors.joining(",\n"));

            String teamOptions = ticket.config().escalationTeams().stream()
                    .map(team -> String.format("""
                        {
                          "text": {"type": "plain_text", "text": %s},
                          "value": %s
                        }""", safeJson(team.label()), safeJson(team.code())))
                    .collect(Collectors.joining(",\n"));

            String privateMetadataQuoted = safeJson(String.format("{\"ticketId\":%d}", ticket.id()));

            return StringSubstitutor.replace(
                    """
                {
                  "type": "modal",
                  "title": { "type": "plain_text", "text": ${titleText}, "emoji": false },
                  "submit": { "type": "plain_text", "text": "Escalate", "emoji": false },
                  "close": { "type": "plain_text", "text": "Cancel", "emoji": false },
                  "callback_id": "ticket-escalate",
                  "clear_on_close": true,
                  "blocks": [
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
                  ],
                  "private_metadata": ${privateMetadata}
                }
                """,
                    Map.of(
                            "titleText", safeJson("Escalate Ticket ID-" + ticket.id()),
                            "tagOptions", tagOptions,
                            "teamOptions", teamOptions,
                            "privateMetadata", privateMetadataQuoted));
        }

        private String safeJson(String value) {
            try {
                return OBJECT_MAPPER.writeValueAsString(value);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
