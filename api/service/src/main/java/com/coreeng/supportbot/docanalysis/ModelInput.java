package com.coreeng.supportbot.docanalysis;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Lays out one model call: each part of data inside its {@link InputTag}, then the instructions, so
 * that nothing in the data follows them. Every tag name is removed from the data first, so data
 * cannot close its own wrapper or open a false one.
 *
 * <p>Removal also deletes legitimate text that reads as a tag, such as the {@code <Page>} in
 * {@code List<Page>}. It does not catch look-alike characters, such as a full-width {@code \uff1c}; the
 * prompts' statement that everything inside the tags is data is the only defence against those.
 *
 * <pre>{@code
 * String prompt = ModelInput.start()
 *         .group(InputTag.DOCUMENTATION, docs -> docs.part(InputTag.PAGE, "path", "a.md", pageText))
 *         .part(InputTag.THREAD, thread)
 *         .instructions(promptText);
 * }</pre>
 */
public final class ModelInput {

    private static final List<String> TAG_NAMES = Arrays.stream(InputTag.values())
            .map(InputTag::tagName)
            .sorted(Comparator.comparingInt(String::length).reversed())
            .toList();
    private static final Pattern INVISIBLE = Pattern.compile("[\\p{Cf}\\p{Cc}&&[^\\n\\r\\t]]");
    private static final Pattern ATTRIBUTE_NAME = Pattern.compile("[a-z][a-z_]*");
    // Text after a tag name only matters for tidiness: with the name gone it cannot form a tag.
    private static final int MAX_TAG_TAIL = 256;
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
     * no quote, angle bracket, control or invisible character, line break or surrounding space.
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
        String result = INVISIBLE.matcher(data).replaceAll("");
        for (int pass = 0; pass <= MAX_PASSES; pass++) {
            String stripped = stripOnce(result);
            if (stripped.equals(result)) {
                return result;
            }
            result = stripped;
        }
        return result.replace("<", "");
    }

    static void requireAttributeValue(String name, String value) {
        if (!value.equals(value.strip())) {
            throw new IllegalArgumentException(name + " must not start or end with a space: " + value);
        }
        if (value.chars().anyMatch(ModelInput::isUnsafeInAttribute)) {
            throw new IllegalArgumentException(name
                    + " must not contain a quote, angle bracket, control or invisible character or line break: "
                    + value);
        }
    }

    private static boolean isUnsafeInAttribute(int c) {
        int type = Character.getType(c);
        return c == '"'
                || c == '<'
                || c == '>'
                || Character.isISOControl(c)
                || type == Character.FORMAT
                || type == Character.LINE_SEPARATOR
                || type == Character.PARAGRAPH_SEPARATOR;
    }

    private static String stripOnce(String text) {
        StringBuilder out = new StringBuilder(text.length());
        int i = 0;
        while (i < text.length()) {
            int end = text.charAt(i) == '<' ? tagEnd(text, i) : i;
            if (end > i) {
                i = end;
            } else {
                out.append(text.charAt(i));
                i++;
            }
        }
        return out.toString();
    }

    /** Where the tag starting at {@code start} ends, or {@code start} when no tag starts there. */
    private static int tagEnd(String text, int start) {
        int i = start + 1;
        while (i < text.length() && isSeparator(text.charAt(i))) {
            i++;
        }
        int nameEnd = nameEnd(text, i);
        if (nameEnd < 0) {
            return start;
        }
        int end = nameEnd;
        while (end < text.length() && (text.charAt(end) == '_' || Character.isDigit(text.charAt(end)))) {
            end++;
        }
        if (end < text.length() && Character.isLetter(text.charAt(end))) {
            return start;
        }
        int limit = Math.min(text.length(), end + MAX_TAG_TAIL);
        for (int tail = end; tail < limit; tail++) {
            char c = text.charAt(tail);
            if (c == '>') {
                return tail + 1;
            }
            if (c == '<') {
                break;
            }
        }
        return end;
    }

    private static int nameEnd(String text, int from) {
        for (String name : TAG_NAMES) {
            if (text.regionMatches(true, from, name, 0, name.length())) {
                return from + name.length();
            }
        }
        return -1;
    }

    private static boolean isSeparator(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c) || c == '/' || c == '\\';
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
