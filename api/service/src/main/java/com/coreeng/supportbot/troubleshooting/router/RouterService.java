package com.coreeng.supportbot.troubleshooting.router;

import com.coreeng.supportbot.config.LlmProps;
import com.coreeng.supportbot.config.TroubleshootingProps;
import com.coreeng.supportbot.troubleshooting.ConditionalOnTroubleshootingEnabled;
import com.coreeng.supportbot.troubleshooting.TroubleshootingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/** One router call: prompt, structured output, parsing and usage. Never returns output it could not parse. */
@Service
@ConditionalOnTroubleshootingEnabled
@Slf4j
public class RouterService {
    static final ResponseFormat RESPONSE_FORMAT = ResponseFormat.builder()
            .type(ResponseFormatType.JSON)
            .jsonSchema(JsonSchema.builder()
                    .name("router_decision")
                    .rootElement(JsonObjectSchema.builder()
                            .addEnumProperty(
                                    "action",
                                    Arrays.stream(RouterAction.values())
                                            .map(Enum::name)
                                            .toList())
                            .addStringProperty("text")
                            .addStringProperty("reason")
                            .required("action", "text", "reason")
                            .additionalProperties(false)
                            .build())
                    .build())
            .build();

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    private final LlmProps llmProps;
    private final TroubleshootingProps props;

    public RouterService(
            ChatModel chatModel, ObjectMapper objectMapper, LlmProps llmProps, TroubleshootingProps props) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.llmProps = llmProps;
        this.props = props;
    }

    public RouterResult decide(TicketContext ticket, List<TroubleshootingEvent> events) {
        ChatRequest request = ChatRequest.builder()
                .messages(
                        SystemMessage.from(RouterPromptBuilder.SYSTEM_PROMPT),
                        UserMessage.from(RouterPromptBuilder.userMessage(ticket, events, props.maxThreadChars())))
                .responseFormat(RESPONSE_FORMAT)
                .build();

        long started = System.nanoTime();
        ChatResponse response;
        try {
            response = chatModel.chat(request);
        } catch (RuntimeException e) {
            log.atWarn().setCause(e).addArgument(ticket::ticketId).log("Router call failed for ticket {}");
            throw new RouterFailureException(
                    "the model call failed (" + e.getClass().getSimpleName() + ")", e);
        }
        long durationMs = (System.nanoTime() - started) / 1_000_000;

        TokenUsage tokens = response.tokenUsage();
        String model = response.modelName() == null ? llmProps.modelName() : response.modelName();
        RouterUsage usage = new RouterUsage(model, count(tokens, true), count(tokens, false), durationMs);
        log.atInfo()
                .addKeyValue("ticketId", ticket.ticketId())
                .addKeyValue("model", usage.model())
                .addKeyValue("inputTokens", usage.inputTokens())
                .addKeyValue("outputTokens", usage.outputTokens())
                .addKeyValue("durationMs", usage.durationMs())
                .addKeyValue("finishReason", response.finishReason())
                .log("Router turn completed");

        return new RouterResult(parse(response.aiMessage().text(), response.finishReason()), usage);
    }

    RouterDecision parse(@Nullable String text, @Nullable FinishReason finishReason) {
        if (finishReason == FinishReason.LENGTH) {
            throw new RouterFailureException("the answer was cut off");
        }
        if (finishReason == FinishReason.CONTENT_FILTER) {
            throw new RouterFailureException("the model declined to answer");
        }
        if (text == null || text.isBlank()) {
            throw new RouterFailureException("the answer was empty");
        }
        JsonNode json;
        try {
            json = objectMapper.readTree(text);
        } catch (Exception e) {
            throw new RouterFailureException("the answer was not valid JSON", e);
        }
        String action = json.path("action").asText("");
        RouterAction routerAction = Arrays.stream(RouterAction.values())
                .filter(candidate -> candidate.name().equals(action))
                .findFirst()
                .orElseThrow(() -> new RouterFailureException("unknown action '" + action + "'"));
        String decisionText = json.path("text").asText("").trim();
        if (decisionText.isEmpty()) {
            throw new RouterFailureException("the answer had no text");
        }
        return new RouterDecision(
                routerAction, decisionText, json.path("reason").asText("").trim());
    }

    private static long count(@Nullable TokenUsage tokens, boolean input) {
        if (tokens == null) {
            return 0;
        }
        Integer value = input ? tokens.inputTokenCount() : tokens.outputTokenCount();
        return value == null ? 0 : value;
    }
}
