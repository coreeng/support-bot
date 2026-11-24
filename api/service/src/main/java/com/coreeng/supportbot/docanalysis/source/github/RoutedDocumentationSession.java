package com.coreeng.supportbot.docanalysis.source.github;

import static dev.langchain4j.model.chat.Capability.RESPONSE_FORMAT_JSON_SCHEMA;

import com.coreeng.supportbot.docanalysis.ContextAnswer;
import com.coreeng.supportbot.docanalysis.DistilledQuestion;
import com.coreeng.supportbot.docanalysis.DocumentationSession;
import com.coreeng.supportbot.docanalysis.InputTag;
import com.coreeng.supportbot.docanalysis.ModelInput;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/** Asks the model which product areas could answer each thread, and returns their pages with the root pages. */
@Slf4j
final class RoutedDocumentationSession implements DocumentationSession {

    /** The routing call named an area the index does not hold. Another call may name the right one. */
    static final String UNKNOWN_AREA = "unknown_area";

    /** The first area chosen, with the root pages, is over the budget. Neither changes for the ticket. */
    static final String AREA_TOO_LARGE = "area_too_large";

    private final String commit;
    private final PageIndex index;
    private final ChatModel chatModel;
    private final Supplier<String> instructions;
    private final int maxAreas;

    /** @param instructions the routing prompt as it is sent, read for each thread */
    RoutedDocumentationSession(
            String commit, PageIndex index, ChatModel chatModel, Supplier<String> instructions, int maxAreas) {
        this.commit = commit;
        this.index = index;
        this.chatModel = chatModel;
        this.instructions = instructions;
        this.maxAreas = maxAreas;
    }

    @Override
    public String version() {
        return commit;
    }

    @Override
    public ContextAnswer findContext(String thread, int budget) {
        String input = ModelInput.start()
                .part(InputTag.PAGE_INDEX, index.text())
                .part(InputTag.THREAD, thread)
                .instructions(instructions.get());

        ChatResponse response;
        try {
            response = chatModel.chat(request(input));
        } catch (RuntimeException e) {
            log.warn("The documentation routing call failed", e);
            return ContextAnswer.Failed.providerError();
        }

        Optional<RoutingAnswer> parsed =
                RoutingAnswer.parse(response.aiMessage().text());
        if (parsed.isEmpty() || parsed.get().areas().size() > maxAreas) {
            return ContextAnswer.Failed.invalidResponse();
        }
        RoutingAnswer answer = parsed.get();
        DistilledQuestion question = new DistilledQuestion(answer.question(), answer.tags());
        if (!answer.documentationQuestion()) {
            return new ContextAnswer.NotADocumentationQuestion(answer.reason(), question);
        }
        List<String> areas = answer.areas();
        if (areas.isEmpty()) {
            return new ContextAnswer.NothingCovers(answer.reason(), question);
        }
        if (!index.areaNames().containsAll(areas)) {
            return new ContextAnswer.Failed(UNKNOWN_AREA, true);
        }
        ContextAnswer.Pages chosen = index.answer(areas, question);
        if (chosen.estimatedTokens() <= budget) {
            return chosen;
        }
        ContextAnswer.Pages first = index.answer(List.of(areas.getFirst()), question);
        if (first.estimatedTokens() <= budget) {
            return first;
        }
        return new ContextAnswer.Failed(AREA_TOO_LARGE, false);
    }

    private ChatRequest request(String input) {
        ChatRequest.Builder request = ChatRequest.builder().messages(UserMessage.from(input));
        if (chatModel.supportedCapabilities().contains(RESPONSE_FORMAT_JSON_SCHEMA)) {
            request.responseFormat(RoutingAnswer.RESPONSE_FORMAT);
        }
        return request.build();
    }
}
