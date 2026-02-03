package com.coreeng.supportbot.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class JwtAuthenticationToken extends AbstractAuthenticationToken {
    private final UserPrincipal principal;
    private final String token;

    public JwtAuthenticationToken(UserPrincipal principal, String token) {
        super(buildAuthorities(principal));
        this.principal = principal;
        this.token = token;
        setAuthenticated(true);
    }

    private static Collection<? extends GrantedAuthority> buildAuthorities(UserPrincipal principal) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

        if (principal.isLeadership()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_LEADERSHIP"));
        }
        if (principal.isSupportEngineer()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_SUPPORT_ENGINEER"));
        }
        if (principal.isEscalation()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ESCALATION"));
        }
        return authorities;
    }

    @Override
    public Object getCredentials() {
        return token;
    }

    @Override
    public UserPrincipal getPrincipal() {
        return principal;
    }
}
