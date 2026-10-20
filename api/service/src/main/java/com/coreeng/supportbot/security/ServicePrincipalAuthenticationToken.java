package com.coreeng.supportbot.security;

import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class ServicePrincipalAuthenticationToken extends AbstractAuthenticationToken {

    /** Held only by service principals; people's authorities come from {@link Role}, which cannot produce it. */
    public static final String AUTHORITY = "elevate:principal:service";

    private final ServicePrincipal principal;

    public ServicePrincipalAuthenticationToken(ServicePrincipal principal) {
        super(List.of(new SimpleGrantedAuthority(AUTHORITY)));
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public ServicePrincipal getPrincipal() {
        return principal;
    }
}
