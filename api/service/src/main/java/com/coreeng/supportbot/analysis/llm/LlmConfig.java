package com.coreeng.supportbot.analysis.llm;

import com.coreeng.supportbot.config.AnalysisProps;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.vertexai.gemini.VertexAiGeminiChatModel;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "analysis.prompt.enabled", havingValue = "true")
@Slf4j
public class LlmConfig {

    @Bean
    @ConditionalOnProperty(prefix = "analysis.llm", name = "provider", havingValue = "vertex", matchIfMissing = true)
    public ChatModel vertexChatModel(AnalysisProps analysisProps) {
        AnalysisProps.Llm llm = analysisProps.llm();
        log.info(
                "Configuring Vertex AI Gemini model: project={}, location={}, model={}",
                llm.vertex().projectId(),
                llm.vertex().location(),
                llm.modelName());

        return VertexAiGeminiChatModel.builder()
                .project(llm.vertex().projectId())
                .location(llm.vertex().location())
                .modelName(llm.modelName())
                .build();
    }

    @Bean
    @ConditionalOnProperty(prefix = "analysis.llm", name = "provider", havingValue = "gateway")
    public ChatModel gatewayChatModel(AnalysisProps analysisProps) {
        AnalysisProps.Llm llm = analysisProps.llm();
        log.info(
                "Configuring AI gateway Gemini model: baseUrl={}, model={}, timeout={}",
                llm.gateway().baseUrl(),
                llm.modelName(),
                llm.gateway().timeout());

        // No apiKey: the gateway authenticates via the Basic header, and the client
        // only sends x-goog-api-key when an apiKey is set.
        return GoogleAiGeminiChatModel.builder()
                .baseUrl(llm.gateway().baseUrl())
                .modelName(llm.modelName())
                .customHeaders(Map.of("Authorization", "Basic " + llm.gateway().basicAuthToken()))
                .timeout(llm.gateway().timeout())
                .build();
    }
}
