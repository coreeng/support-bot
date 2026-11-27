package com.coreeng.supportbot.docanalysis;

import static java.util.stream.Collectors.joining;

import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Lays out one model call: each part of data inside its {@link InputTag}, then the instructions, so
 * that nothing in the data follows them. Every tag name is removed from the data first, so data
 * cannot close its own wrapper or open a false one.
 *
 * <p>Removal also deletes legitimate text that reads as a tag, such as the {@code <Page>} in
 * {@code List<Page>}. It does not catch a tag broken up by an invisible character, or one written
 * with look-alike characters such as a full-width {@code \uff1c}; the prompts' statement that
 * everything inside the tags is data is the only defence against those.
 *
 * <pre>{@code
 * String prompt = ModelInput.start()
 *         .group(InputTag.DOCUMENTATION, docs -> docs.part(InputTag.PAGE, "path", "a.md", pageText))
 *         .part(InputTag.THREAD, thread)
 *         .instructions(promptText);
 * }</pre>
 */
public final class ModelInput {

    // Possessive and bounded so that no input makes it backtrack. What follows the name is removed
    // only for tidiness: with the name gone the rest cannot form a tag.
    private static final Pattern TAG = Pattern.compile(
            "<[\\s/]*+(?:"
                    + Arrays.stream(InputTag.values())
                            .map(InputTag::tagName)
                            .sorted(Comparator.comparingInt(String::length).reversed())
                            .collect(joining("|"))
                    + ")(?![a-z])(?:[^<>]{0,256}>)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTRIBUTE_NAME = Pattern.compile("[a-z][a-z_]*");
    // Each pass removes one layer of tags nested inside each other; deeper nesting is hostile.
    private static final int MAX_PASSES = 4;

    private final StringBuilder text = new StringBuilder();
    private int depth;
    private boolean finished;

    private ModelInput() {}

    public static ModelInput start() {
        return new ModelInput();
    }

    public ModelInput part(InputTag tag, String data) {
        return element(tag, null, null, data);
    }

    /**
     * A part with one attribute, such as a page's path. The value is sent as it is, so it must hold
     * no quote, angle bracket or control character.
     */
    public ModelInput part(InputTag tag, String attributeName, String attributeValue, String data) {
        if (!ATTRIBUTE_NAME.matcher(attributeName).matches()) {
            throw new IllegalArgumentException("attribute name must be lowercase letters and underscores");
        }
        requireAttributeValue(attributeName, attributeValue);
        return element(tag, attributeName, attributeValue, data);
    }

    public ModelInput group(InputTag tag, Consumer<ModelInput> parts) {
        requireUnfinished();
        separateFromPrevious();
        text.append('<').append(tag.tagName()).append(">\n");
        depth++;
        parts.accept(this);
        depth--;
        text.append("</").append(tag.tagName()).append(">\n");
        return this;
    }

    public String instructions(String instructions) {
        requireUnfinished();
        if (depth > 0) {
            throw new IllegalStateException("instructions cannot be placed inside a group");
        }
        finished = true;
        separateFromPrevious();
        return text.append(instructions).toString();
    }

    /** The data as it is sent; checks against what the model saw compare with this. */
    public static String removeTags(String data) {
        String result = data;
        for (int pass = 0; pass <= MAX_PASSES; pass++) {
            String stripped = TAG.matcher(result).replaceAll("");
            if (stripped.equals(result)) {
                return result;
            }
            result = stripped;
        }
        return result.replace("<", "");
    }

    static boolean isAttributeValue(String value) {
        return value.chars().noneMatch(c -> c == '"' || c == '<' || c == '>' || Character.isISOControl(c));
    }

    static void requireAttributeValue(String name, String value) {
        if (!isAttributeValue(value)) {
            throw new IllegalArgumentException(
                    name + " must not contain a quote, angle bracket or control character: " + value);
        }
    }

    private ModelInput element(
            InputTag tag, @Nullable String attributeName, @Nullable String attributeValue, String data) {
        requireUnfinished();
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

    private void requireUnfinished() {
        if (finished) {
            throw new IllegalStateException("the instructions are placed; nothing can follow them");
        }
    }

    private void separateFromPrevious() {
        if (depth == 0 && !text.isEmpty()) {
            text.append('\n');
        }
    }
}
