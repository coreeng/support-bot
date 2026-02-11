package com.coreeng.supportbot.testkit;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.matching.AnythingPattern;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.text.StringSubstitutor;

@Builder
@Getter
public class RatingRequestMessage {
    private final long ticketId;

    @RequiredArgsConstructor
    public static class Receiver implements StubWithResult.Receiver<RatingRequestMessage> {
        private static final String EXPECTED_TEXT = "How was your support experience?";

        private final long expectedTicketId;

        @Override
        public MappingBuilder configureStub(MappingBuilder stubBuilder) {
            return stubBuilder
                    .withFormParam("text", new AnythingPattern())
                    .withFormParam("blocks", new AnythingPattern());
        }

        @Override
        public RatingRequestMessage assertAndExtractResult(ServeEvent servedStub) {
            String text = servedStub.getRequest().formParameter("text").firstValue();
            assertThat(text).isEqualTo(EXPECTED_TEXT);

            String blocksRaw = servedStub.getRequest().formParameter("blocks").firstValue();
            assertThatJson(blocksRaw).isEqualTo(buildExpectedBlocksJson());

            return RatingRequestMessage.builder().ticketId(expectedTicketId).build();
        }

        private String buildExpectedBlocksJson() {
            return StringSubstitutor.replace("""
                [
                  {
                    "type": "section",
                    "text": {
                      "type": "mrkdwn",
                      "text": "*How was your support experience?* \\n_Your feedback helps us improve our service. Ratings are collected anonymously to protect your privacy._"
                    }
                  },
                  {
                    "type": "actions",
                    "elements": [
                      {
                        "type": "button",
                        "action_id": "rating-submit-1",
                        "text": {"type": "plain_text", "text": "⭐"},
                        "value": "{\\"ticketId\\":${ticketId},\\"rating\\":1}"
                      },
                      {
                        "type": "button",
                        "action_id": "rating-submit-2",
                        "text": {"type": "plain_text", "text": "⭐⭐"},
                        "value": "{\\"ticketId\\":${ticketId},\\"rating\\":2}"
                      },
                      {
                        "type": "button",
                        "action_id": "rating-submit-3",
                        "text": {"type": "plain_text", "text": "⭐⭐⭐"},
                        "value": "{\\"ticketId\\":${ticketId},\\"rating\\":3}"
                      },
                      {
                        "type": "button",
                        "action_id": "rating-submit-4",
                        "text": {"type": "plain_text", "text": "⭐⭐⭐⭐"},
                        "value": "{\\"ticketId\\":${ticketId},\\"rating\\":4}"
                      },
                      {
                        "type": "button",
                        "action_id": "rating-submit-5",
                        "text": {"type": "plain_text", "text": "⭐⭐⭐⭐⭐"},
                        "value": "{\\"ticketId\\":${ticketId},\\"rating\\":5}"
                      }
                    ]
                  }
                ]
                """, Map.of("ticketId", expectedTicketId));
        }
    }
}
