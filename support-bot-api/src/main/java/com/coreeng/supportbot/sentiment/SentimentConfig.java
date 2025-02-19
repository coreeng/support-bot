package com.coreeng.supportbot.sentiment;

import com.coreeng.supportbot.sentiment.client.SentimentAIClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import lombok.RequiredArgsConstructor;
import org.eclipse.jetty.client.HttpClient;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JettyClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
public class SentimentConfig {
    private final ObjectMapper objectMapper;

    @Bean
    public RestClient sentimentRestClient() {
        return RestClient.builder()
            .baseUrl("http://localhost:8081")
            .requestFactory(requestFactory())
            .messageConverters(ImmutableList.of(
                new MappingJackson2HttpMessageConverter(objectMapper)
            ))
            .build();
    }

    @NotNull
    private static JettyClientHttpRequestFactory requestFactory() {
        HttpClient httpClient = new HttpClient();
        httpClient.setIdleTimeout(Duration.ofHours(1).toMillis());
        JettyClientHttpRequestFactory factory = new JettyClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofHours(1));
        return factory;
    }

    @Bean
    public SentimentAIClient sentimentAIClient() {
        return new SentimentAIClient(sentimentRestClient());
    }
}
