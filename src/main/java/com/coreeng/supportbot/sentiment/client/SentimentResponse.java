package com.coreeng.supportbot.sentiment.client;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Builder(toBuilder = true)
@Jacksonized
public class SentimentResponse {
    private Message message;
    private Sentiment sentiment;
}
