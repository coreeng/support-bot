package com.coreeng.supportbot.analysis.llm;

import com.coreeng.supportbot.config.AnalysisProps;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.vertexai.gemini.VertexAiGeminiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "analysis.prompt.enabled", havingValue = "true")
@Slf4j
public class LlmConfig {

    @Bean
    public ChatModel chatModel(AnalysisProps analysisProps) {
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
}
