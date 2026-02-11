package com.coreeng.supportbot.sentiment.client;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Sentiment(
        double positive,
        double neutral,
        double negative,
        @JsonProperty("overallSentiment") Conclusion conclusion) {
    public Sentiment(double positive, double neutral, double negative) {
        this(positive, neutral, negative, Conclusion.from(positive, neutral, negative));
    }

    public enum Conclusion {
        positive,
        neutral,
        negative;

        public static Conclusion from(double positive, double neutral, double negative) {
            if (positive > negative && positive > neutral) {
                return Conclusion.positive;
            }
            if (negative > positive && negative > neutral) {
                return Conclusion.negative;
            }
            return Conclusion.neutral;
        }
    }
}
