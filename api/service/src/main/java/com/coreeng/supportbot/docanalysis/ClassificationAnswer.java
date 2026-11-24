package com.coreeng.supportbot.docanalysis;

import com.fasterxml.jackson.databind.JsonNode;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * What the classification call answered, read but not yet checked against the pages or the thread.
 *
 * @param question blank when the model gave none
 * @param tags as the model gave them; anything in the field that is not a list of strings is no tags
 */
record ClassificationAnswer(
        Verdict verdict,
        String question,
        List<String> tags,
        String answerSummary,
        List<String> docPaths,
        String evidenceQuote,
        String rationale) {

    enum Verdict {
        CORRECT,
        MISSING,
        INCORRECT,
        NOT_APPLICABLE,
        UNDETERMINED;

        String code() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    static final ResponseFormat RESPONSE_FORMAT = ResponseFormat.builder()
            .type(ResponseFormatType.JSON)
            .jsonSchema(JsonSchema.builder()
                    .name("documentation_classification")
                    .rootElement(JsonObjectSchema.builder()
                            .addEnumProperty(
                                    "classification",
                                    Arrays.stream(Verdict.values())
                                            .map(Verdict::code)
                                            .toList())
                            .addStringProperty("question")
                            .addProperty("tags", stringArray())
                            .addStringProperty("answer_summary")
                            .addProperty("doc_paths", stringArray())
                            .addStringProperty("evidence_quote")
                            .addStringProperty("rationale")
                            .required(
                                    "classification",
                                    "question",
                                    "tags",
                                    "answer_summary",
                                    "doc_paths",
                                    "evidence_quote",
                                    "rationale")
                            .additionalProperties(false)
                            .build())
                    .build())
            .build();

    ClassificationAnswer {
        tags = List.copyOf(tags);
        docPaths = List.copyOf(docPaths);
    }

    /** Empty when the text is not one JSON object, alone or in a code fence, that keeps the contract. */
    static Optional<ClassificationAnswer> parse(@Nullable String text) {
        Optional<JsonNode> object = JsonAnswer.object(text);
        if (object.isEmpty()) {
            return Optional.empty();
        }
        JsonNode root = object.get();
        Optional<Verdict> verdict = verdict(root.get("classification"));
        List<String> docPaths = JsonAnswer.strings(root.get("doc_paths"));
        String rationale = JsonAnswer.text(root.get("rationale"));
        if (verdict.isEmpty() || docPaths == null || rationale.isBlank()) {
            return Optional.empty();
        }
        List<String> tags = JsonAnswer.strings(root.get("tags"));
        return Optional.of(new ClassificationAnswer(
                verdict.get(),
                JsonAnswer.text(root.get("question")),
                tags == null ? List.of() : tags,
                JsonAnswer.text(root.get("answer_summary")),
                docPaths,
                JsonAnswer.text(root.get("evidence_quote")),
                rationale));
    }

    private static JsonArraySchema stringArray() {
        return JsonArraySchema.builder().items(new JsonStringSchema()).build();
    }

    private static Optional<Verdict> verdict(@Nullable JsonNode node) {
        if (node == null || !node.isTextual()) {
            return Optional.empty();
        }
        return Arrays.stream(Verdict.values())
                .filter(verdict -> verdict.code().equals(node.asText()))
                .findFirst();
    }
}
