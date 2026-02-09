package com.coreeng.supportbot.security;

import com.google.common.collect.ImmutableList;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

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
        var builder = ImmutableList.<GrantedAuthority>builder();
        builder.add(Role.user.grantedAuthority());

        if (principal.isLeadership()) {
            builder.add(Role.leadership.grantedAuthority());
        }
        if (principal.isSupportEngineer()) {
            builder.add(Role.supportEngineer.grantedAuthority());
        }
        if (principal.isEscalation()) {
            builder.add(Role.escalation.grantedAuthority());
        }
        return builder.build();
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
