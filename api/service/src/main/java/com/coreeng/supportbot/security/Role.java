package com.coreeng.supportbot.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@Getter
public enum Role {
    user("ROLE_USER"),
    leadership("ROLE_LEADERSHIP"),
    supportEngineer("ROLE_SUPPORT_ENGINEER"),
    escalation("ROLE_ESCALATION");

    private final String authority;

    Role(String authority) {
        this.authority = authority;
    }

    public GrantedAuthority grantedAuthority() {
        return new SimpleGrantedAuthority(authority);
    }
}
