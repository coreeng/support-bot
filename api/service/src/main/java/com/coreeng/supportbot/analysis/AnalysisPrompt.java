package com.coreeng.supportbot.analysis;

import static java.util.Objects.requireNonNull;

public record AnalysisPrompt(int version, String content) {

    public AnalysisPrompt {
        requireNonNull(content, "content");
    }
}
