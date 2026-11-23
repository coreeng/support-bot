package com.coreeng.supportbot.config;

import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Backs {@link ConditionalOnDocumentationAnalysis}. Binds {@link DocumentationAnalysisProps} itself,
 * so the condition and the properties cannot disagree about a value or a default.
 */
class OnDocumentationAnalysisCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return outcome(bind(context), context, ConditionMessage.forCondition(ConditionalOnDocumentationAnalysis.class));
    }

    static DocumentationAnalysisProps bind(ConditionContext context) {
        return Binder.get(context.getEnvironment())
                .bindOrCreate(DocumentationAnalysisProps.PREFIX, DocumentationAnalysisProps.class);
    }

    static ConditionOutcome outcome(
            DocumentationAnalysisProps props, ConditionContext context, ConditionMessage.Builder message) {
        if (!props.enabled()) {
            return ConditionOutcome.noMatch(message.because(DocumentationAnalysisProps.PREFIX + ".enabled is false"));
        }
        String provider = context.getEnvironment().getProperty(OnLlmEnabledCondition.PROPERTY);
        if (OnLlmEnabledCondition.provider(provider) == LlmProvider.NONE) {
            return ConditionOutcome.noMatch(message.because(OnLlmEnabledCondition.PROPERTY + " is none"));
        }
        return ConditionOutcome.match(message.because(DocumentationAnalysisProps.PREFIX + ".enabled is true and "
                + OnLlmEnabledCondition.PROPERTY + " is set"));
    }
}
