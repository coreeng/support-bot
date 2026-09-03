package com.coreeng.supportbot.summary;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.config.AnalysisProps;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** The model name stamped on a snapshot comes from the model itself when it says who it is. */
class LlmSummaryServiceTest {

    private static final String CONFIGURED_MODEL = "gemini-2.5-flash";

    @Test
    void modelNameFallsBackToTheConfiguredNameWhenTheModelDoesNotSelfDescribe() {
        // Vertex's model does not override defaultRequestParameters(), so the configured id is the
        // only name available — and it is the right one for that provider.
        ChatModel silent = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest chatRequest) {
                return reply("ignored");
            }
        };

        assertThat(new LlmSummaryService(silent, props()).modelName()).isEqualTo(CONFIGURED_MODEL);
    }

    @Test
    void modelNamePrefersWhatTheModelReportsAboutItself() {
        ChatModel selfDescribing = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest chatRequest) {
                return reply("ignored");
            }

            @Override
            public ChatRequestParameters defaultRequestParameters() {
                return ChatRequestParameters.builder()
                        .modelName("something-else")
                        .build();
            }
        };

        assertThat(new LlmSummaryService(selfDescribing, props()).modelName()).isEqualTo("something-else");
    }

    @Test
    void modelNameIgnoresABlankSelfDescription() {
        ChatModel blank = new ChatModel() {
            @Override
            public ChatResponse doChat(ChatRequest chatRequest) {
                return reply("ignored");
            }

            @Override
            public ChatRequestParameters defaultRequestParameters() {
                return ChatRequestParameters.builder().modelName(" ").build();
            }
        };

        assertThat(new LlmSummaryService(blank, props()).modelName()).isEqualTo(CONFIGURED_MODEL);
    }

    private static ChatResponse reply(String text) {
        return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
    }

    private static AnalysisProps props() {
        AnalysisProps.Llm llm = new AnalysisProps.Llm(
                CONFIGURED_MODEL,
                Duration.ofMillis(1),
                new AnalysisProps.Vertex(true, "test-project", "europe-west2"),
                new AnalysisProps.Proxy(false, "", new AnalysisProps.Proxy.Auth(""), Duration.ofSeconds(30)),
                new AnalysisProps.Stub(false, false));
        return new AnalysisProps(
                llm,
                new AnalysisProps.Bundle("classpath:placeholder-analysis-bundle.zip"),
                new AnalysisProps.Prompt(true));
    }
}
