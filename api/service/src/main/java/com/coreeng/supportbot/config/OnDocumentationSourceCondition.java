package com.coreeng.supportbot.config;

import java.util.Map;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** Backs {@link ConditionalOnDocumentationSource}. */
class OnDocumentationSourceCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        ConditionMessage.Builder message = ConditionMessage.forCondition(ConditionalOnDocumentationSource.class);
        DocumentationAnalysisProps props = OnDocumentationAnalysisCondition.bind(context);
        ConditionOutcome enabled = OnDocumentationAnalysisCondition.outcome(props, context, message);
        if (!enabled.isMatch()) {
            return enabled;
        }
        DocumentationSourceType selected = props.source().selectedType();
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
