package com.coreeng.supportbot.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final String authorizationHeader = "Authorization";
    private static final String bearerPrefix = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        var authHeader = request.getHeader(authorizationHeader);

        if (authHeader != null && authHeader.startsWith(bearerPrefix)) {
            var token = authHeader.substring(bearerPrefix.length());
            var principalOpt = jwtService.validateToken(token);

            principalOpt.ifPresent(principal -> {
                var authentication = new JwtAuthenticationToken(principal, token);
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Authenticated user: {}", principal.email());
            });
        }

        filterChain.doFilter(request, response);
    }
}
