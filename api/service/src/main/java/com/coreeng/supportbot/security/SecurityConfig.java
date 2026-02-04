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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

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

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers("/oauth2/**", "/login/**").permitAll()
                .requestMatchers("/auth/token").permitAll()
                .requestMatchers("/actuator/health", "/actuator/prometheus").permitAll()
                // Slack webhook endpoint - uses Slack's own signing secret verification
                .requestMatchers("/slack/events").permitAll()
                // Dashboard restricted to leadership or support engineers
                .requestMatchers("/dashboard/**").hasAnyRole("LEADERSHIP", "SUPPORT_ENGINEER")
                // All other endpoints require authentication
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .successHandler(oauth2SuccessHandler())
            )
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
            properties,
            jwtService,
            authCodeStore,
            teamService,
            supportTeamService
        );
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        var configuration = new CorsConfiguration();

        var origins = properties.cors().allowedOrigins();
        if (origins != null && !origins.isBlank()) {
            configuration.setAllowedOrigins(Arrays.asList(origins.split(",")));
        }

        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
