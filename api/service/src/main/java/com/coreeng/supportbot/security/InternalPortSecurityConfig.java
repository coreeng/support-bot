package com.coreeng.supportbot.security;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * Every request on the internal port is a service principal, trusted because the NetworkPolicy admits only
 * Elevate's erasure orchestrator to that port (ADR-010). It may reach the erasure endpoints and nothing else.
 */
@Configuration
@ConditionalOnProperty(name = "internal-port.enabled", havingValue = "true")
@EnableConfigurationProperties(InternalPortProperties.class)
public class InternalPortSecurityConfig {

    static final String[] SERVICE_PATHS = {"/api/erasure-requests", "/api/erasure-requests/**"};

    @Bean
    @Order(1)
    public SecurityFilterChain internalPortFilterChain(HttpSecurity http, InternalPortProperties properties)
            throws Exception {
        http.securityMatcher(request -> request.getLocalPort() == properties.port())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        .requestMatchers(SERVICE_PATHS)
                        .hasAuthority(ServicePrincipalAuthenticationToken.AUTHORITY)
                        .anyRequest()
                        .denyAll())
                .addFilterBefore(new InternalPortCallerFilter(), AnonymousAuthenticationFilter.class);
        return http.build();
    }
}
