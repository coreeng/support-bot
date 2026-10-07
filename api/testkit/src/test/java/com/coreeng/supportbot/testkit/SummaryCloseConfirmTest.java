package com.coreeng.supportbot.testkit;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class SummaryCloseConfirmTest {
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static final String MATCHING_PRIVATE_METADATA = """
            {
              "ticketId": 42,
              "status": "closed",
              "authorsTeam": "connected-app",
              "tags": ["networking", "support"],
              "impact": "productionBlocking",
              "assignedTo": null,
              "confirmed": false
            }
            """;

    @Test
    void matchingPrivateMetadataPassesJackson3JsonUnitComparison() throws Exception {
        assertThatNoException()
                .isThrownBy(() -> confirm(MATCHING_PRIVATE_METADATA).assertMatches(values()));
    }

    @Test
    void mismatchedPrivateMetadataIsStillRejected() {
        String mismatchedMetadata = MATCHING_PRIVATE_METADATA.replace("\"support\"", "\"other\"");

        assertThatThrownBy(() -> confirm(mismatchedMetadata).assertMatches(values()))
                .isInstanceOf(AssertionError.class);
    }

    private static FullSummaryFormSubmission.Values values() {
        return FullSummaryFormSubmission.Values.builder()
                .status(Ticket.Status.closed)
                .team("connected-app")
                .tags(ImmutableList.of("networking", "support"))
                .impact("productionBlocking")
                .build();
    }

    private static SummaryCloseConfirm confirm(String privateMetadata) throws Exception {
        var view = MAPPER.readTree("""
                {
                  "callback_id": "ticket-summary-confirm",
                  "type": "modal",
                  "title": {"type": "plain_text", "text": "Closing Ticket", "emoji": false},
                  "submit": {"type": "plain_text", "text": "Confirm", "emoji": false},
                  "close": {"type": "plain_text", "text": "Cancel", "emoji": false},
                  "blocks": [
                    {
                      "type": "section",
                      "text": {
                        "type": "mrkdwn",
                        "text": "Ticket has `2` unresolved escalations. Closing the ticket will close all related escalations.\\nAre you sure?"
                      }
                    }
                  ]
                }
                """);
        return new SummaryCloseConfirm(42, 2, privateMetadata, view);
    }
}
