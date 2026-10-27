package com.coreeng.supportbot.testkit;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import lombok.RequiredArgsConstructor;

/** The ephemeral "thank you" posted after a star rating is recorded. */
public record RatingConfirmationMessage() {
    @RequiredArgsConstructor
    public static class Receiver implements StubWithResult.Receiver<RatingConfirmationMessage> {
        private final int rating;

        @Override
        public MappingBuilder configureStub(MappingBuilder stubBuilder) {
            return stubBuilder.withFormParam(
                    "text",
                    equalTo(String.format(
                            "Thank you for your %d star rating! Your feedback has been recorded.", rating)));
        }

        @Override
        public RatingConfirmationMessage assertAndExtractResult(ServeEvent servedStub) {
            return new RatingConfirmationMessage();
        }
    }
}
