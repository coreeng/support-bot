package com.coreeng.supportbot.docanalysis;

import java.util.List;

/** What a thread asks, as a source distilled it, with the topic tags the source gave it. */
public record DistilledQuestion(String text, List<String> tags) {

    public DistilledQuestion {
        text = text.strip();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        tags = List.copyOf(tags);
    }
}
