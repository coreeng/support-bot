package com.coreeng.supportbot.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@Getter
public enum Role {
    USER("ROLE_USER"),
    LEADERSHIP("ROLE_LEADERSHIP"),
    SUPPORT_ENGINEER("ROLE_SUPPORT_ENGINEER"),
    ESCALATION("ROLE_ESCALATION");

    private final String authority;

    Role(String authority) {
        this.authority = authority;
    }

    public GrantedAuthority grantedAuthority() {
        return new SimpleGrantedAuthority(authority);
    }
}
