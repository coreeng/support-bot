package com.coreeng.supportbot.docanalysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Reads a model's JSON answer the same way for every call that asks for one, a source's included. */
public final class JsonAnswer {

    private static final ObjectMapper MAPPER =
            new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Pattern FENCED = Pattern.compile("```[a-zA-Z]*+[ \\t]*+\\n(.*)```", Pattern.DOTALL);

    private JsonAnswer() {}

    /** Empty when the text is not one JSON object, alone or in a code fence. */
    public static Optional<JsonNode> object(@Nullable String text) {
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
        return root != null && root.isObject() ? Optional.of(root) : Optional.empty();
    }

    /** The field's text, stripped, or empty when it is absent or not a string. */
    public static String text(@Nullable JsonNode node) {
        return node != null && node.isTextual() ? node.asText().strip() : "";
    }

    /** Null when the field is present and is not a list of strings; empty when it is absent. */
    public static @Nullable List<String> strings(@Nullable JsonNode node) {
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
