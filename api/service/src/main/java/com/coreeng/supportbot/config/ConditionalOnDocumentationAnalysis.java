package com.coreeng.supportbot.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Conditional;

/**
 * Registers the annotated bean only when the documentation analysis is on: {@code
 * documentation-analysis.enabled} is true and an LLM provider is selected.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnDocumentationAnalysisCondition.class)
public @interface ConditionalOnDocumentationAnalysis {}
