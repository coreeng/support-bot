package com.coreeng.supportbot.sentiment;

import com.coreeng.supportbot.sentiment.client.SentimentAIClient;
import com.coreeng.supportbot.sentiment.rest.SentimentAnalysisController;
import com.coreeng.supportbot.slack.client.SlackClient;
import com.coreeng.supportbot.teams.SupportTeamService;
import com.coreeng.supportbot.ticket.TicketQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import java.time.Duration;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.eclipse.jetty.client.HttpClient;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JettyClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty("ai.sentiment-analysis.enabled")
public class SentimentConfig {
    private final ObjectMapper objectMapper;

    @NonNull private static JettyClientHttpRequestFactory requestFactory() {
        HttpClient httpClient = new HttpClient();
        httpClient.setIdleTimeout(Duration.ofHours(1).toMillis());
        JettyClientHttpRequestFactory factory = new JettyClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofHours(1));
        return factory;
    }

    @Bean
    public RestClient sentimentRestClient() {
        return RestClient.builder()
                .baseUrl("http://localhost:8081")
                .requestFactory(requestFactory())
                .messageConverters(ImmutableList.of(new MappingJackson2HttpMessageConverter(objectMapper)))
                .build();
    }

    @Bean
    public SentimentAIClient sentimentAIClient() {
        return new SentimentAIClient(sentimentRestClient());
    }

    @Bean
    public SentimentRepository sentimentRepository(final TicketQueryService ticketQueryService, final ZoneId timezone) {
        return new SentimentInMemoryRepository(ticketQueryService, timezone);
    }

    @Bean
    public SentimentService sentimentService(
            TicketQueryService ticketQueryService,
            SupportTeamService supportTeamService,
            SlackClient slackClient,
            SentimentAIClient client) {
        return new SentimentService(ticketQueryService, supportTeamService, slackClient, client);
    }

    @Bean
    public SentimentQueryService sentimentQueryService(SentimentRepository repository) {
        return new SentimentQueryService(repository);
    }

    @Bean
    public SentimentAnalysisJob sentimentAnalysisJob(
            SentimentRepository repository, SentimentService sentimentService) {
        return new SentimentAnalysisJob(repository, sentimentService);
    }

    @Bean
    public SentimentAnalysisController sentimentAnalysisController(
            SentimentAnalysisJob job, SentimentQueryService queryService) {
        return new SentimentAnalysisController(job, queryService);
    }
}
