package com.coreeng.supportbot.security;

import com.coreeng.supportbot.teams.SupportTeamService;
import com.coreeng.supportbot.teams.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
@RequiredArgsConstructor
public class SecurityConfig {
    private final SecurityProperties properties;
    private final JwtService jwtService;
    private final AuthCodeStore authCodeStore;
    private final TeamService teamService;
    private final SupportTeamService supportTeamService;
    private final OAuth2AvailabilityChecker oauth2AvailabilityChecker;
    private final AllowListService allowListService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers("/oauth2/**", "/login/**")
                        .permitAll()
                        .requestMatchers("/auth/token", "/auth/oauth-url", "/auth/oauth/exchange")
                        .permitAll()
                        .requestMatchers("/health", "/prometheus")
                        .permitAll()
                        // Slack webhook endpoint - uses Slack's own signing secret verification
                        .requestMatchers("/slack/events")
                        .permitAll()
                        // Dashboard restricted to leadership or support engineers
                        .requestMatchers("/dashboard/**")
                        .hasAnyRole("LEADERSHIP", "SUPPORT_ENGINEER")
                        // All other endpoints require authentication
                        .anyRequest()
                        .authenticated())
                .oauth2Login(oauth2 -> {
                    if (oauth2AvailabilityChecker.isOAuth2Available()) {
                        oauth2.successHandler(oauth2SuccessHandler());
                    } else {
                        oauth2.disable();
                    }
                })
                .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(testAuthBypassFilter(), JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtService);
    }

    @Bean
    public TestAuthBypassFilter testAuthBypassFilter() {
        return new TestAuthBypassFilter(properties);
    }

    @Bean
    public OAuth2SuccessHandler oauth2SuccessHandler() {
        return new OAuth2SuccessHandler(
                properties, jwtService, authCodeStore, teamService, supportTeamService, allowListService);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
