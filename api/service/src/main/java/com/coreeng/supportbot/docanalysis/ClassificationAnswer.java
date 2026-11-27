package com.coreeng.supportbot.docanalysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    private static final ObjectMapper MAPPER =
            new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Pattern FENCED = Pattern.compile("```[a-zA-Z]*+[ \\t]*+\\n(.*)```", Pattern.DOTALL);

    ClassificationAnswer {
        tags = List.copyOf(tags);
        docPaths = List.copyOf(docPaths);
    }

    /** Empty when the text is not one JSON object, alone or in a code fence, that keeps the contract. */
    static Optional<ClassificationAnswer> parse(@Nullable String text) {
        if (text == null) {
            return Optional.empty();
        }
        String json = text.strip();
        Matcher fenced = FENCED.matcher(json);
        if (fenced.matches()) {
            json = fenced.group(1).strip();
        }
        if (!json.startsWith("{")) {
            return Optional.empty();
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
        if (root == null || !root.isObject()) {
            return Optional.empty();
        }
        Optional<Verdict> verdict = verdict(root.get("classification"));
        List<String> docPaths = strings(root.get("doc_paths"));
        String rationale = text(root.get("rationale"));
        if (verdict.isEmpty() || docPaths == null || rationale.isBlank()) {
            return Optional.empty();
        }
        List<String> tags = strings(root.get("tags"));
        return Optional.of(new ClassificationAnswer(
                verdict.get(),
                text(root.get("question")),
                tags == null ? List.of() : tags,
                text(root.get("answer_summary")),
                docPaths,
                text(root.get("evidence_quote")),
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

    private static String text(@Nullable JsonNode node) {
        return node != null && node.isTextual() ? node.asText().strip() : "";
    }

    /** Null when the field is present and is not a list of strings; empty when it is absent. */
    private static @Nullable List<String> strings(@Nullable JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            return null;
        }
        List<String> values = new ArrayList<>();
        for (JsonNode element : node) {
            if (!element.isTextual()) {
                return null;
            }
            values.add(element.asText().strip());
        }
        return values;
    }
}
