package com.coreeng.supportbot.analysis.llm;

import com.coreeng.supportbot.config.AnalysisProps;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.vertexai.gemini.VertexAiGeminiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class LlmConfig {

    @Bean
    @ConditionalOnProperty(name = "analysis.prompt.enabled", havingValue = "true")
    public ChatModel chatModel(AnalysisProps analysisProps) {
        log.info(
                "Configuring Vertex AI Gemini model: project={}, location={}, model={}",
                analysisProps.vertex().projectId(),
                analysisProps.vertex().location(),
                analysisProps.vertex().modelName());

        return VertexAiGeminiChatModel.builder()
                .project(analysisProps.vertex().projectId())
                .location(analysisProps.vertex().location())
                .modelName(analysisProps.vertex().modelName())
                .build();
    }
}
