package com.coreeng.supportbot.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Conditional;

/**
 * Registers the annotated bean only when an LLM provider is selected, i.e. {@code llm.provider} is
 * anything other than {@code none}. This is the one gate for the analysis run and the Support
 * Summary page; the per-provider {@code ChatModel} beans additionally match on the provider's name.
 *
 * <p>A {@code @ConditionalOnProperty} cannot express "not none", hence the dedicated condition.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(OnLlmEnabledCondition.class)
public @interface ConditionalOnLlmEnabled {}
