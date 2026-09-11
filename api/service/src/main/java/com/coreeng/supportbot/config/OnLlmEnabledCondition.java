package com.coreeng.supportbot.config;

import java.util.Arrays;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** Backs {@link ConditionalOnLlmEnabled}: matches when {@code llm.provider} is set and not {@code none}. */
class OnLlmEnabledCondition extends SpringBootCondition {

    static final String PROPERTY = "llm.provider";

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        ConditionMessage.Builder message = ConditionMessage.forCondition(ConditionalOnLlmEnabled.class);
        LlmProvider provider = provider(context.getEnvironment().getProperty(PROPERTY));
        if (provider == LlmProvider.NONE) {
            return ConditionOutcome.noMatch(message.because(PROPERTY + " is none"));
        }
        return ConditionOutcome.match(
                message.because(PROPERTY + " is " + provider.name().toLowerCase(Locale.ROOT)));
    }

    /**
     * Same lenient parse Spring Boot applies when it binds {@link LlmProps#provider()}, so the
     * condition and the properties record cannot disagree about a value. A value that is not a
     * provider name fails here, before {@code LlmProps} is bound, with a message naming the
     * choices.
     */
    static LlmProvider provider(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return LlmProvider.NONE;
        }
        String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        return Arrays.stream(LlmProvider.values())
                .filter(candidate -> candidate.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        PROPERTY + " must be one of none, vertex, proxy, stub" + " but was '" + value + "'"));
    }
}
