package com.coreeng.supportbot.elevate;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record ElevateIntegrityItem(
        Type type,
        @Nullable String journeyId,
        @Nullable String journeyName,
        @Nullable String journeyProductId,
        @Nullable UUID userId,
        @Nullable String userName,
        @Nullable String userProductId) {
    public enum Type {
        ORPHAN_JOURNEY("orphanJourney"),
        ORPHAN_USER("orphanUser"),
        MISSING_ASSIGNMENT("missingAssignment"),
        CROSS_PRODUCT_ASSIGNMENT("crossProductAssignment");

        private final String wireValue;

        Type(String wireValue) {
            this.wireValue = wireValue;
        }

        @JsonValue
        public String wireValue() {
            return wireValue;
        }
    }
}
