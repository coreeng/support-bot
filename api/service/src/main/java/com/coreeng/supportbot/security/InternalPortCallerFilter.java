package com.coreeng.supportbot.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates every request it sees as a service principal. Only {@link InternalPortSecurityConfig}'s chain
 * runs it; it is deliberately not a bean, which would register it as a servlet filter on every port.
 */
class InternalPortCallerFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new ServicePrincipalAuthenticationToken(new ServicePrincipal(request.getRemoteAddr())));
        filterChain.doFilter(request, response);
    }
}
