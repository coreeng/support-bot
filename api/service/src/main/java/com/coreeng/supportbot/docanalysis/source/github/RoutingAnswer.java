package com.coreeng.supportbot.docanalysis.source.github;

import com.coreeng.supportbot.docanalysis.JsonAnswer;
import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * What the routing call answered, read but not yet checked against the index.
 *
 * @param tags as the model gave them; anything in the field that is not a list of strings is no tags
 * @param areas without repeats, most likely first
 */
record RoutingAnswer(
        String question, List<String> tags, boolean documentationQuestion, List<String> areas, String reason) {

    static final ResponseFormat RESPONSE_FORMAT = ResponseFormat.builder()
            .type(ResponseFormatType.JSON)
            .jsonSchema(JsonSchema.builder()
                    .name("documentation_routing")
                    .rootElement(JsonObjectSchema.builder()
                            .addStringProperty("question")
                            .addProperty("tags", stringArray())
                            .addBooleanProperty("is_documentation_question")
                            .addProperty("areas", stringArray())
                            .addStringProperty("reason")
                            .required("question", "tags", "is_documentation_question", "areas", "reason")
                            .additionalProperties(false)
                            .build())
                    .build())
            .build();

    RoutingAnswer {
        tags = List.copyOf(tags);
        areas = List.copyOf(areas);
    }

    /**
     * Empty when the text is not one JSON object, alone or in a code fence, with a question, a
     * reason, a yes or no to whether it is a documentation question, and the areas as a list of
     * names.
     */
    static Optional<RoutingAnswer> parse(@Nullable String text) {
        Optional<JsonNode> object = JsonAnswer.object(text);
        if (object.isEmpty()) {
            return Optional.empty();
        }
        JsonNode root = object.get();
        String question = JsonAnswer.text(root.get("question"));
        JsonNode documentationQuestion = root.get("is_documentation_question");
        JsonNode areasField = root.get("areas");
        List<String> areas = areasField == null ? null : JsonAnswer.strings(areasField);
        String reason = JsonAnswer.text(root.get("reason"));
        if (question.isEmpty()
                || documentationQuestion == null
                || !documentationQuestion.isBoolean()
                || areas == null
                || reason.isEmpty()) {
            return Optional.empty();
        }
        List<String> tags = JsonAnswer.strings(root.get("tags"));
        return Optional.of(new RoutingAnswer(
                question,
                tags == null ? List.of() : tags,
                documentationQuestion.booleanValue(),
                areas.stream().distinct().toList(),
                reason));
    }

    private static JsonArraySchema stringArray() {
        return JsonArraySchema.builder().items(new JsonStringSchema()).build();
    }
}
