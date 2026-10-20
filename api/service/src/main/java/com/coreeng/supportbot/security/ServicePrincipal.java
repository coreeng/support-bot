package com.coreeng.supportbot.security;

/** A caller on the internal port. The network admitted it, so its address is all that identifies it. */
public record ServicePrincipal(String remoteAddress) {}
