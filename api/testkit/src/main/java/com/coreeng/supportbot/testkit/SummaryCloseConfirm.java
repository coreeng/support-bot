package com.coreeng.supportbot.testkit;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class SummaryCloseConfirm {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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

        String expectedText = "Ticket has `" + numberOfOpenEscalations + "` unresolved escalations."
                + " Closing the ticket will close all related escalations.\n"
                + "Are you sure?";

        var expectedBlocks = OBJECT_MAPPER.createArrayNode();
        var section = OBJECT_MAPPER.createObjectNode();
        var text = OBJECT_MAPPER.createObjectNode();
        section.put("type", "section");
        text.put("type", "mrkdwn");
        text.put("text", expectedText);
        section.set("text", text);
        expectedBlocks.add(section);
        assertThatJson(viewJson.get("blocks")).isEqualTo(expectedBlocks);

        assertThatNoException().isThrownBy(() -> {
            JsonNode pm = OBJECT_MAPPER.readTree(privateMetadataRaw);
            String assignedToJson =
                    expected.assignedTo() != null ? String.format("\"%s\"", expected.assignedTo()) : "null";
            String expectedPmJson = String.format(
                    """
                    {
                      "ticketId": %d,
                      "status": "%s",
                      "authorsTeam": "%s",
                      "tags": %s,
                      "impact": "%s",
                      "assignedTo": %s,
                      "confirmed": false
                    }
                    """,
                    ticketId,
                    expected.status(),
                    expected.team(),
                    OBJECT_MAPPER.writeValueAsString(expected.tags()),
                    expected.impact(),
                    assignedToJson);
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

    public record Receiver(long ticketId, long numberOfOpenEscalations)
            implements ViewSubmissionResponseReceiver<SummaryCloseConfirm> {
        @Override
        public SummaryCloseConfirm parse(String responseBody) {
            try {
                JsonNode root = OBJECT_MAPPER.readTree(responseBody);
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
