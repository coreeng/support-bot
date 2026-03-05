package com.coreeng.supportbot.analysis.llm;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import java.util.List;
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
public class FakeChatLanguageModel implements ChatLanguageModel {

    private static final String STUB_ANALYSIS_RESPONSE = """
            Ticket: 0
            Primary Driver: Knowledge Gap
            Category: Monitoring & Troubleshooting Tenant Applications
            Platform Feature: workload compute
            Reason: Functional test analysis result
            """;

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        return Response.from(AiMessage.from(STUB_ANALYSIS_RESPONSE));
    }
}
