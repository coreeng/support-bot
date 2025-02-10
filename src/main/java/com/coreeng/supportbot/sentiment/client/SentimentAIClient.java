package com.coreeng.supportbot.sentiment.client;

import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

@RequiredArgsConstructor
public class SentimentAIClient {
    private final RestClient sentimentClient;

    public SentimentResponse classify(Message request) {
        return sentimentClient.post()
            .uri("/classify")
            .body(request)
            .retrieve()
            .body(SentimentResponse.class);
    }

    public ImmutableList<SentimentResponse> classifyBulk(Messages request) {
        return sentimentClient.post()
            .uri("/classify-bulk")
            .body(request)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    }
}
