package com.coreeng.supportbot.docanalysis;

public sealed interface OpenResult {

    record Opened(DocumentationSession session) implements OpenResult {}

    record Unavailable(String reason) implements OpenResult {
        public Unavailable {
            if (reason.isBlank()) {
                throw new IllegalArgumentException("reason must not be blank");
            }
        }
    }
}
