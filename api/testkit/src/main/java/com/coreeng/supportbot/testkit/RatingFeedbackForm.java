package com.coreeng.supportbot.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.google.common.collect.ImmutableList;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;

/** The follow-up feedback modal opened after a rating is clicked. */
public record RatingFeedbackForm(String privateMetadata, ImmutableList<String> inputLabels) {
    @RequiredArgsConstructor
    public static class Receiver implements StubWithResult.Receiver<RatingFeedbackForm> {
        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

        private final long expectedTicketId;
        private final int expectedRating;

        @Override
        public MappingBuilder configureStub(MappingBuilder stubBuilder) {
            return stubBuilder;
        }

        @Override
        public RatingFeedbackForm assertAndExtractResult(ServeEvent servedStub) {
            try {
                JsonNode view = OBJECT_MAPPER.readTree(
                        servedStub.getRequest().formParameter("view").firstValue());
                assertThat(view.get("callback_id").asText()).isEqualTo(RatingFeedbackFormSubmission.CALLBACK_ID);

                String privateMetadata = view.get("private_metadata").asText();
                JsonNode metadata = OBJECT_MAPPER.readTree(privateMetadata);
                assertThat(metadata.get("ticketId").asLong()).isEqualTo(expectedTicketId);
                assertThat(metadata.get("rating").asInt()).isEqualTo(expectedRating);
                assertThat(metadata.get("ratingId").asText()).isNotBlank();

                ImmutableList<String> labels = StreamSupport.stream(
                                view.get("blocks").spliterator(), false)
                        .filter(b -> "input".equals(b.get("type").asText()))
                        .map(b -> b.get("label").get("text").asText())
                        .collect(ImmutableList.toImmutableList());
                return new RatingFeedbackForm(privateMetadata, labels);
            } catch (Exception e) {
                throw new AssertionError("Failed to parse rating feedback view", e);
            }
        }
    }
}
