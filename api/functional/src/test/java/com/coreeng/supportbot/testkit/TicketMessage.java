package com.coreeng.supportbot.testkit;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.text.StringSubstitutor;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.http.FormParameter;
import com.github.tomakehurst.wiremock.matching.AnythingPattern;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.jayway.jsonpath.JsonPath;

import lombok.Builder;
import lombok.Getter;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.JSON;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

@Builder
@Getter
public class TicketMessage {
    public static final String fullSummaryButtonActionId = "ticket-summary-view";
    public static final String escalateButtonActionId = "ticket-escalate";

    private final long ticketId;
    private final String channelId;
    private final MessageTs ts;
    private final MessageTs queryTs;
    private final Ticket.Status status;
    private final Instant statusChangedAt;

    public void assertMatches(SupportBotClient.TicketResponse response) {
        assertThat(ticketId).isEqualTo(response.id());
        assertThat(channelId).isEqualTo(response.channelId());
        assertThat(ts).isEqualTo(response.formMessage().ts());
        assertThat(queryTs).isEqualTo(response.query().ts());
        assertThat(status.code()).isEqualTo(response.status());
        assertThat(statusChangedAt)
            .isEqualTo(response.logs().getLast().date().truncatedTo(ChronoUnit.SECONDS));
    }

    public static class Receiver implements StubWithResult.Receiver<TicketMessage> {
        private static final ObjectMapper objectMapper = new ObjectMapper();
        private static final Pattern headerRegex = Pattern.compile("^Ticket Created: ID-(?<id>\\d+)$");
        private static final Pattern statusRegex = Pattern.compile("^(?<status>Opened|Closed): <!date\\^(?<ts>[^\\^]+)\\^\\{date_short_pretty} at \\{time}\\|(?<tsString>[^>]+)>$");

        @Override
        public MappingBuilder configureStub(MappingBuilder stubBuilder) {
            return stubBuilder
                .withFormParam("text", new AnythingPattern())
                .withFormParam("blocks", new AnythingPattern())
                .withFormParam("attachments", new AnythingPattern())
                .withFormParam("link_names", new AnythingPattern())
                .withFormParam("mrkdwn", new AnythingPattern())
                .withFormParam("unfurl_links", new AnythingPattern())
                .withFormParam("unfurl_media", new AnythingPattern())
                .withFormParam("reply_broadcast", new AnythingPattern());
        }

        @Override
        public TicketMessage assertAndExtractResult(ServeEvent servedStub) throws IOException {
            FormParameter textParam = servedStub.getRequest().formParameter("text");
            String text = textParam.firstValue();
            Matcher headerMatcher = headerRegex.matcher(text);
            assertThat(headerMatcher).matches();
            String ticketIdStr = headerMatcher.group("id");
            long ticketId = Long.parseLong(ticketIdStr);

            FormParameter blocksParam = servedStub.getRequest().formParameter("blocks");
            FormParameter attachmentsParam = servedStub.getRequest().formParameter("attachments");
            assertBlocks(blocksParam, ticketId);
            AttachmentView attachmentView = assertAttachmentsAndReturnView(attachmentsParam, ticketId);

            JsonNode responseBody = objectMapper.readTree(servedStub.getResponse().getBody());
            String channelId = responseBody.get("channel").asText();
            MessageTs ts = MessageTs.fromTsString(responseBody.get("ts").asText());
            MessageTs threadTs = MessageTs.fromTsString(responseBody.get("message").get("thread_ts").asText());

            return TicketMessage.builder()
                .ticketId(ticketId)
                .channelId(channelId)
                .ts(ts)
                .queryTs(threadTs)
                .status(attachmentView.status())
                .statusChangedAt(attachmentView.statusChangedAt())
                .build();
        }

        private AttachmentView assertAttachmentsAndReturnView(FormParameter attachmentsParam, long ticketId) throws JsonProcessingException {
            assertThat(attachmentsParam).isNotNull();
            assertThat(attachmentsParam.getValues()).hasSize(1);
            String attachmentsRaw = attachmentsParam.getValues().getFirst();
            JsonNode attachmentsJson = objectMapper.readTree(attachmentsRaw);

            // Check if the ticket is closed or opened based on the color
            String color = JsonPath.read(attachmentsRaw, "$[0].color");
            Ticket.Status expectedStatus = Ticket.Status.fromColor(color);

            // Build button elements dynamically based on ticket status
            String buttonElements = buildButtonElements(ticketId, expectedStatus);

            String expectedJson = StringSubstitutor.replace("""
                 [
                   {
                     "fallback": "#{json-unit.ignore}",
                     "color": "${expectedColor}",
                     "blocks": [
                       {
                         "type": "divider"
                       },
                       {
                         "type": "section",
                         "text": {
                           "type": "mrkdwn",
                           "text": "#{json-unit.ignore}"
                         }
                       },
                       {
                         "type": "actions",
                         "elements": ${buttonElements}
                       },
                       {
                         "type": "context",
                         "elements": [
                           {
                             "type": "plain_text",
                             "text": ":pushpin: Options above supplied for Support engineers. Please ignore...",
                             "emoji": true
                           }
                         ]
                       }
                     ]
                   }
                ]""", Map.of(
                "expectedColor", expectedStatus.colorHex(),
                "buttonElements", buttonElements
            ));

            assertThatJson(attachmentsJson).isEqualTo(expectedJson);

            String fallback = JsonPath.read(attachmentsRaw, "$[0].fallback");
            String messageHeader = JsonPath.read(attachmentsRaw, "$[0].blocks[1].text.text");

            Matcher statusMatcher = statusRegex.matcher(messageHeader);
            assertThat(statusMatcher).matches();
            assertThat(fallback).isEqualTo(messageHeader);
            String status = statusMatcher.group("status");
            String statusTs = statusMatcher.group("ts");
            String statusTsString = statusMatcher.group("tsString");

            assertThat(status).isEqualTo(expectedStatus.label());
            Instant statusChangedAt = Instant.ofEpochSecond(Long.parseLong(statusTs));
            assertThat(statusChangedAt.truncatedTo(ChronoUnit.MINUTES).toString())
                .isEqualTo(statusTsString);
            return new AttachmentView(statusChangedAt, expectedStatus);
        }

        private void assertBlocks(FormParameter blocksParam, long ticketId) {
            assertThat(blocksParam).isNotNull();
            assertThat(blocksParam.getValues()).hasSize(1)
                .first()
                .asInstanceOf(JSON)
                .isEqualTo(String.format("""
                    [{"type":"section","text":{"type":"mrkdwn","text":"*Ticket Created*: `ID-%d`"}}]
                    """, ticketId));
        }

        private String buildButtonElements(long ticketId, Ticket.Status expectedStatus) {
            if (Ticket.Status.opened.equals(expectedStatus)) {
                // Opened ticket: both "Full Summary" and "Escalate" buttons
                return String.format("""
                    [
                      {
                        "type": "button",
                        "text": {
                          "type": "plain_text",
                          "text": "Full Summary"
                        },
                        "action_id": "ticket-summary-view",
                        "value": "{\\"ticketId\\":%d}"
                      },
                      {
                        "type": "button",
                        "text": {
                          "type": "plain_text",
                          "text": "Escalate"
                        },
                        "action_id": "ticket-escalate",
                        "value": "{\\"ticketId\\":%d}"
                      }
                    ]""", ticketId, ticketId);
            } else {
                // Closed ticket: only "Full Summary" button
                return String.format("""
                    [
                      {
                        "type": "button",
                        "text": {
                          "type": "plain_text",
                          "text": "Full Summary"
                        },
                        "action_id": "ticket-summary-view",
                        "value": "{\\"ticketId\\":%d}"
                      }
                    ]""", ticketId);
            }
        }

        public record AttachmentView(
            Instant statusChangedAt,
            Ticket.Status status
        ) {
        }
    }
}
