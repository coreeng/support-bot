package com.coreeng.supportbot.troubleshooting;

import com.coreeng.supportbot.config.ConditionalOnLlmEnabled;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Registers the bean only when troubleshooting is enabled and an LLM provider is selected. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ConditionalOnProperty(name = "troubleshooting.enabled", havingValue = "true")
@ConditionalOnLlmEnabled
public @interface ConditionalOnTroubleshootingEnabled {}
