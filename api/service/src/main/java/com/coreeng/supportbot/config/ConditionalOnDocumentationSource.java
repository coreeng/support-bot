package com.coreeng.supportbot.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Conditional;

/**
 * Registers the annotated bean only when the documentation analysis is on (see {@link
 * ConditionalOnDocumentationAnalysis}) and {@link #value()} is the selected source. Each source's own
 * configuration carries this, so exactly one source is built.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnDocumentationSourceCondition.class)
public @interface ConditionalOnDocumentationSource {
    DocumentationSourceType value();
}
