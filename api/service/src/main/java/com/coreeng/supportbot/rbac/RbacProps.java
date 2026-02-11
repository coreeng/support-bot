package com.coreeng.supportbot.rbac;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rbac")
public record RbacProps(boolean enabled) {}
