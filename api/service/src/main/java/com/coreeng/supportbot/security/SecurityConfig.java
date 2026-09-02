package com.coreeng.supportbot.security;

import com.coreeng.supportbot.teams.SupportTeamService;
import com.coreeng.supportbot.teams.TeamService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.access.RequestMatcherDelegatingAccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties({SecurityProperties.class, JwtGroupsProperties.class})
@RequiredArgsConstructor
public class SecurityConfig {
    private final SecurityProperties properties;
    private final JwtService jwtService;
    private final AuthCodeStore authCodeStore;
    private final TeamService teamService;
    private final SupportTeamService supportTeamService;
    private final OAuth2AvailabilityChecker oauth2AvailabilityChecker;
    private final AllowListService allowListService;
    private final JwtGroupTeamMerger jwtGroupTeamMerger;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource)
            throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Unhandled exceptions are re-dispatched by the container to Spring Boot's
                        // /error handler. JwtAuthenticationFilter is a OncePerRequestFilter, which skips
                        // ERROR dispatches, so without this rule every 5xx would be reported to the
                        // caller as a 401 — and the UI signs the user out on 401.
                        .dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        // Public endpoints
                        .requestMatchers("/oauth2/**", "/login/**")
                        .permitAll()
                        .requestMatchers("/auth/token", "/auth/oauth-url", "/auth/oauth/exchange", "/auth/providers")
                        .permitAll()
                        .requestMatchers("/health", "/prometheus")
                        .permitAll()
                        // Slack webhook endpoint - uses Slack's own signing secret verification
                        .requestMatchers("/slack/events")
                        .permitAll()
                        // Feature-enabled checks are open to any authenticated user (not just leadership/
                        // support engineers) so the UI sidebar can safely query them for everyone to decide
                        // whether to show the nav item, without a 403 for users lacking that role.
                        // Listed ahead of the role-gated rules below so the check keeps working if
                        // one of those is ever widened to a wildcard.
                        .requestMatchers("/elevate/enabled", "/summary/enabled")
                        .authenticated()
                        // Dashboard restricted to leadership or support engineers
                        .requestMatchers("/dashboard/**", "/summary-data/results", "/elevate/**")
                        .hasAnyRole("LEADERSHIP", "SUPPORT_ENGINEER")
                        // Support Summary page. Deliberately NOT support-engineer-only: serving it
                        // triggers the backfill server-side, so leadership viewers must be able to
                        // reach it without being granted the /analysis/run permission.
                        .requestMatchers(HttpMethod.GET, "/summary")
                        .hasAnyRole("LEADERSHIP", "SUPPORT_ENGINEER")
                        // Prompt texts are read-only and shown by the summary page's View Prompt
                        // dialog, which leadership can open — so both prompts follow the page's
                        // roles rather than the support-engineer-only analysis actions.
                        .requestMatchers(HttpMethod.GET, "/summary/prompt", "/analysis/prompt")
                        .hasAnyRole("LEADERSHIP", "SUPPORT_ENGINEER")
                        // Summary data export/import is restricted to support engineers
                        .requestMatchers("/summary-data/**")
                        .hasAnyRole("SUPPORT_ENGINEER")
                        // Analysis endpoints restricted to support engineers
                        .requestMatchers("/analysis/status", "/analysis/run")
                        .hasAnyRole("SUPPORT_ENGINEER")
                        // All other endpoints require authentication
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            // Return 401 for API endpoints with missing or expired auth
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"error\":\"Unauthorized\"}");
                        })
                        .accessDeniedHandler(accessDeniedHandler()));

        if (oauth2AvailabilityChecker.isOAuth2Available()) {
            http.oauth2Login(oauth2 -> oauth2.successHandler(oauth2SuccessHandler()));
        }

        http.addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(testAuthBypassFilter(), JwtAuthenticationFilter.class);

        return http.build();
    }

    private AccessDeniedHandler accessDeniedHandler() {
        var handlers = new LinkedHashMap<RequestMatcher, AccessDeniedHandler>();
        AccessDeniedHandler jsonForbidden = (request, response, accessDeniedException) -> {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Forbidden\"}");
        };
        handlers.put(PathPatternRequestMatcher.withDefaults().matcher("/analysis/prompt"), jsonForbidden);
        handlers.put(PathPatternRequestMatcher.withDefaults().matcher("/summary/prompt"), jsonForbidden);
        return new RequestMatcherDelegatingAccessDeniedHandler(handlers, new AccessDeniedHandlerImpl());
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
                supportTeamService,
                allowListService,
                jwtGroupTeamMerger);
    }

    @Bean
    public RestTemplate restTemplate() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        return new RestTemplate(factory);
    }
}
