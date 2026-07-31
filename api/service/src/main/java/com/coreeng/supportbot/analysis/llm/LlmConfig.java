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
    @ConditionalOnProperty(
            prefix = "analysis.llm.vertex",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    public ChatModel vertexChatModel(AnalysisProps analysisProps) {
        AnalysisProps.Llm llm = analysisProps.llm();
        log.info(
                "Configuring Vertex AI model: project={}, location={}, model={}",
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
    @ConditionalOnProperty(prefix = "analysis.llm.proxy", name = "enabled", havingValue = "true")
    public ChatModel proxyChatModel(AnalysisProps analysisProps) {
        AnalysisProps.Llm llm = analysisProps.llm();
        log.info(
                "Configuring proxied model: baseUrl={}, model={}, timeout={}",
                llm.proxy().baseUrl(),
                llm.modelName(),
                llm.proxy().timeout());

        // No apiKey: the proxy authenticates via the Basic header, and the client
        // only sends x-goog-api-key when an apiKey is set.
        return GoogleAiGeminiChatModel.builder()
                .baseUrl(llm.proxy().baseUrl())
                .modelName(llm.modelName())
                .customHeaders(
                        Map.of("Authorization", "Basic " + llm.proxy().auth().basicAuthToken()))
                .timeout(llm.proxy().timeout())
                .build();
    }
}
