package com.coreeng.supportbot.docanalysis;

import static java.util.stream.Collectors.joining;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Lays out one model call: each part of data inside its {@link InputTag}, then the instructions, so
 * that nothing in the data follows them. Every tag name is removed from the data first, so data
 * cannot close its own wrapper or open a false one.
 *
 * <pre>{@code
 * String prompt = ModelInput.start()
 *         .group(InputTag.DOCUMENTATION, docs -> docs.part(InputTag.PAGE, "path", "a.md", pageText))
 *         .part(InputTag.THREAD, thread)
 *         .instructions(promptText);
 * }</pre>
 */
public final class ModelInput {

    private static final String TAG_NAMES =
            Arrays.stream(InputTag.values()).map(InputTag::tagName).collect(joining("|"));
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
    private static final Pattern WHOLE_TAG = Pattern.compile("<\\s*/?\\s*(?:" + TAG_NAMES + ")\\b[^<>\\n]*>", FLAGS);
    private static final Pattern UNCLOSED_TAG = Pattern.compile("<\\s*/?\\s*(?:" + TAG_NAMES + ")\\b", FLAGS);
    private static final Pattern ATTRIBUTE_NAME = Pattern.compile("[a-z][a-z_]*");

    private final StringBuilder text = new StringBuilder();
    private int depth;

    private ModelInput() {}

    public static ModelInput start() {
        return new ModelInput();
    }

    public ModelInput part(InputTag tag, String data) {
        return element(tag, null, null, data);
    }

    /**
     * A part with one attribute, such as a page's path. The value is sent as it is, so it must hold
     * no quote, angle bracket or line break.
     */
    public ModelInput part(InputTag tag, String attributeName, String attributeValue, String data) {
        if (!ATTRIBUTE_NAME.matcher(attributeName).matches()) {
            throw new IllegalArgumentException("attribute name must be lowercase letters and underscores");
        }
        requireAttributeValue(attributeName, attributeValue);
        return element(tag, attributeName, attributeValue, data);
    }

    public ModelInput group(InputTag tag, Consumer<ModelInput> parts) {
        separateFromPrevious();
        text.append('<').append(tag.tagName()).append(">\n");
        depth++;
        parts.accept(this);
        depth--;
        text.append("</").append(tag.tagName()).append(">\n");
        return this;
    }

    public String instructions(String instructions) {
        if (depth > 0) {
            throw new IllegalStateException("instructions cannot be placed inside a group");
        }
        separateFromPrevious();
        return text.append(instructions).toString();
    }

    /** The data as it is sent; checks against what the model saw compare with this. */
    public static String removeTags(String data) {
        String result = data;
        String previous;
        do {
            previous = result;
            result = removeUntilStable(WHOLE_TAG, result);
            result = UNCLOSED_TAG.matcher(result).replaceAll("");
        } while (!result.equals(previous));
        return result;
    }

    private static String removeUntilStable(Pattern pattern, String text) {
        String result = text;
        String previous;
        do {
            previous = result;
            result = pattern.matcher(result).replaceAll("");
        } while (!result.equals(previous));
        return result;
    }

    static void requireAttributeValue(String name, String value) {
        if (value.chars().anyMatch(c -> c == '"' || c == '<' || c == '>' || c == '\n' || c == '\r')) {
            throw new IllegalArgumentException(
                    name + " must not contain a quote, angle bracket or line break: " + value);
        }
    }

    private ModelInput element(
            InputTag tag, @Nullable String attributeName, @Nullable String attributeValue, String data) {
        separateFromPrevious();
        text.append('<').append(tag.tagName());
        if (attributeName != null) {
            text.append(' ')
                    .append(attributeName)
                    .append("=\"")
                    .append(attributeValue)
                    .append('"');
        }
        text.append(">\n");
        String cleaned = removeTags(data);
        text.append(cleaned);
        if (!cleaned.isEmpty() && !cleaned.endsWith("\n")) {
            text.append('\n');
        }
        text.append("</").append(tag.tagName()).append(">\n");
        return this;
    }

    private void separateFromPrevious() {
        if (depth == 0 && !text.isEmpty()) {
            text.append('\n');
        }
    }
}
