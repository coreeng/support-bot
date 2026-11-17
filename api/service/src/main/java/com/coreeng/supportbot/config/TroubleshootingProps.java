package com.coreeng.supportbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Troubleshooting sessions (ADR-012). Off unless enabled, and only active when an LLM provider is
 * also selected, since the router runs on the shared {@code ChatModel}.
 *
 * @param maxThreadChars how much of the session transcript the router is sent; older events are
 *     dropped first
 */
@ConfigurationProperties(prefix = "troubleshooting")
public record TroubleshootingProps(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("40000") int maxThreadChars) {}
