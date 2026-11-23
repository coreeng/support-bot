package com.coreeng.supportbot.docanalysis;

import org.jspecify.annotations.Nullable;

/**
 * @param id identifies the page to the model and in stored results; for a source that reads files,
 *     its path
 * @param area the product area the page belongs to, or null when it belongs to none
 */
public record DocumentationPage(String id, @Nullable String area, String text) {

    public DocumentationPage {
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        ModelInput.requireAttributeValue("id", id);
        if (area != null && area.isBlank()) {
            throw new IllegalArgumentException("area must not be blank when present");
        }
    }
}
