package com.coreeng.supportbot.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;

import lombok.RequiredArgsConstructor;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

@RequiredArgsConstructor
public class SummaryCloseConfirm {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final long ticketId;
    private final long numberOfOpenEscalations;
    private final String privateMetadataRaw;
    private final JsonNode viewJson;

    public SummaryCloseConfirm assertMatches(FullSummaryFormSubmission.Values expected) {
        assertThat(viewJson.get("callback_id").asText()).isEqualTo("ticket-summary-confirm");
        assertThat(viewJson.get("type").asText()).isEqualTo("modal");
        assertThatJson(viewJson.get("title")).isEqualTo("""
            { "type": "plain_text", "text": "Closing Ticket", "emoji": false }
        """);
        assertThatJson(viewJson.get("submit")).isEqualTo("""
            { "type": "plain_text", "text": "Confirm", "emoji": false }
        """);
        assertThatJson(viewJson.get("close")).isEqualTo("""
            { "type": "plain_text", "text": "Cancel", "emoji": false }
        """);

        String expectedText = "Ticket has `" + numberOfOpenEscalations + "` unresolved escalations." +
            " Closing the ticket will close all related escalations.\n" +
            "Are you sure?";

        var expectedBlocks = objectMapper.createArrayNode();
        var section = objectMapper.createObjectNode();
        var text = objectMapper.createObjectNode();
        section.put("type", "section");
        text.put("type", "mrkdwn");
        text.put("text", expectedText);
        section.set("text", text);
        expectedBlocks.add(section);
        assertThatJson(viewJson.get("blocks")).isEqualTo(expectedBlocks);

        assertThatNoException()
            .isThrownBy(() -> {
                JsonNode pm = objectMapper.readTree(privateMetadataRaw);
                String expectedPmJson = String.format("""
                    {
                      "ticketId": %d,
                      "status": "%s",
                      "authorsTeam": "%s",
                      "tags": %s,
                      "impact": "%s",
                      "confirmed": false
                    }
                    """,
                    ticketId,
                    expected.status(),
                    expected.team(),
                    objectMapper.writeValueAsString(expected.tags()),
                    expected.impact()
                );
                assertThatJson(pm).isEqualTo(expectedPmJson);
            });
        return this;
    }

    public ViewSubmission toSubmission(String triggerIdForConfirm) {
        String pmEscaped = privateMetadataRaw;
        return new ViewSubmission() {
            @Override
            public String triggerId() {
                return triggerIdForConfirm;
            }

            @Override
            public String callbackId() {
                return "ticket-summary-confirm";
            }

            @Override
            public String privateMetadata() {
                return pmEscaped;
            }

            @Override
            public ImmutableList<Value> values() {
                return ImmutableList.of();
            }

            @Override
            public String viewType() {
                return "modal";
            }
        };
    }

    public record Receiver(long ticketId, long numberOfOpenEscalations) implements ViewSubmissionResponseReceiver<SummaryCloseConfirm> {
        @Override
        public SummaryCloseConfirm parse(String responseBody) {
            try {
                JsonNode root = objectMapper.readTree(responseBody);
                JsonNode payload = root.has("payload") ? root.get("payload") : root;
                assertThat(payload.get("response_action").asText()).isEqualTo("update");
                JsonNode view = payload.get("view");
                String privateMetadata = view.get("private_metadata").asText();
                return new SummaryCloseConfirm(ticketId, numberOfOpenEscalations, privateMetadata, view);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        }
}


