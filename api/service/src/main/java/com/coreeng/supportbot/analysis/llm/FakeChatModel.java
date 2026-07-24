package com.coreeng.supportbot.analysis.llm;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fake LLM for functional tests. Returns a dummy analysis response
 * so tests don't need real Vertex AI credentials.
 */
@Profile("functionaltests")
@Primary
@Component
public class FakeChatModel implements ChatModel {

    private static final String STUB_ANALYSIS_RESPONSE = """
            Ticket: 0
            Primary Driver: Knowledge Gap
            Category: Monitoring & Troubleshooting Tenant Applications
            Platform Feature: workload compute
            Reason: Functional test analysis result
            """;

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        return ChatResponse.builder()
                .aiMessage(AiMessage.from(STUB_ANALYSIS_RESPONSE))
                .build();
    }
}
