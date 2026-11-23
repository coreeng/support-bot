package com.coreeng.supportbot.config;

import java.util.Map;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** Backs {@link ConditionalOnDocumentationSource}. */
class OnDocumentationSourceCondition extends SpringBootCondition {

    static final String ENABLED_PROPERTY = "documentation-analysis.enabled";

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        ConditionMessage.Builder message = ConditionMessage.forCondition(ConditionalOnDocumentationSource.class);
        Binder binder = Binder.get(context.getEnvironment());
        if (!binder.bind(ENABLED_PROPERTY, Boolean.class).orElse(false)) {
            return ConditionOutcome.noMatch(message.because(ENABLED_PROPERTY + " is false"));
        }
        String provider = context.getEnvironment().getProperty(OnLlmEnabledCondition.PROPERTY);
        if (OnLlmEnabledCondition.provider(provider) == LlmProvider.NONE) {
            return ConditionOutcome.noMatch(message.because(OnLlmEnabledCondition.PROPERTY + " is none"));
        }
        DocumentationSourceType selected = DocumentationSourceType.parse(
                binder.bind(DocumentationSourceType.PROPERTY, String.class).orElse("github"));
        DocumentationSourceType required = required(metadata);
        if (selected != required) {
            return ConditionOutcome.noMatch(message.because(DocumentationSourceType.PROPERTY + " is "
                    + selected.configValue() + ", not " + required.configValue()));
        }
        return ConditionOutcome.match(
                message.because(DocumentationSourceType.PROPERTY + " is " + selected.configValue()));
    }

    private static DocumentationSourceType required(AnnotatedTypeMetadata metadata) {
        Map<String, Object> attributes = Objects.requireNonNull(
                metadata.getAnnotationAttributes(ConditionalOnDocumentationSource.class.getName()));
        return (DocumentationSourceType) Objects.requireNonNull(attributes.get("value"));
    }
}
