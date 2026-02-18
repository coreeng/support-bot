package com.coreeng.supportbot.security;

import com.coreeng.supportbot.teams.SupportTeamService;
import com.coreeng.supportbot.teams.Team;
import com.coreeng.supportbot.teams.TeamService;
import com.coreeng.supportbot.teams.TeamType;
import com.google.common.collect.ImmutableList;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {
    private static final Pattern LEADERSHIP_PATTERN = Pattern.compile("leadership", Pattern.CASE_INSENSITIVE);
    private static final Pattern SUPPORT_PATTERN = Pattern.compile("support", Pattern.CASE_INSENSITIVE);
    private static final Pattern ESCALATION_PATTERN = Pattern.compile("escalation", Pattern.CASE_INSENSITIVE);

    private final SecurityProperties properties;
    private final JwtService jwtService;
    private final AuthCodeStore authCodeStore;
    private final TeamService teamService;
    private final SupportTeamService supportTeamService;
    private final AllowListService allowListService;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        var oauth2User = (OAuth2User) authentication.getPrincipal();
        var email = extractEmail(oauth2User);
        if (!allowListService.isAllowed(email)) {
            log.warn("Allow-list rejected user during OAuth2 redirect login");
            var redirectUri = UriComponentsBuilder.fromUriString(
                            properties.oauth2().redirectUri())
                    .queryParam("error", "user_not_allowed")
                    .build()
                    .toUriString();
            response.sendRedirect(redirectUri);
            return;
        }
        var name = extractName(oauth2User);

        log.info("OAuth2 login successful for user");

        var teams = teamService.listTeamsByUserEmail(email);
        var roles = computeRoles(email, teams);

        var principal = new UserPrincipal(email, name, teams, roles);

        var jwt = jwtService.generateToken(principal);
        var code = authCodeStore.storeToken(jwt);

        var redirectUri = UriComponentsBuilder.fromUriString(properties.oauth2().redirectUri())
                .queryParam("code", code)
                .build()
                .toUriString();

        log.debug("Redirecting to UI with auth code");
        response.sendRedirect(redirectUri);
    }

    private String extractEmail(OAuth2User oauth2User) {
        var email = oauth2User.getAttribute("email");
        if (email != null) {
            return email.toString().toLowerCase(Locale.ROOT);
        }
        var preferredUsername = oauth2User.getAttribute("preferred_username");
        if (preferredUsername != null) {
            return preferredUsername.toString().toLowerCase(Locale.ROOT);
        }
        throw new IllegalStateException("Unable to extract email from OAuth2 user");
    }

    private String extractName(OAuth2User oauth2User) {
        var name = oauth2User.getAttribute("name");
        if (name != null) {
            return name.toString();
        }
        var givenName = oauth2User.getAttribute("given_name");
        var familyName = oauth2User.getAttribute("family_name");
        if (givenName != null || familyName != null) {
            return ((givenName != null ? givenName : "") + " " + (familyName != null ? familyName : "")).trim();
        }
        return extractEmail(oauth2User);
    }

    private ImmutableList<Role> computeRoles(String email, ImmutableList<Team> teams) {
        var roles = ImmutableList.<Role>builder();
        roles.add(Role.USER);

        if (computeIsLeadership(email, teams)) {
            roles.add(Role.LEADERSHIP);
        }
        if (computeIsSupportEngineer(email, teams)) {
            roles.add(Role.SUPPORT_ENGINEER);
        }
        if (computeIsEscalation(teams)) {
            roles.add(Role.ESCALATION);
        }

        return roles.build();
    }

    private boolean computeIsLeadership(String email, ImmutableList<Team> teams) {
        return supportTeamService.isLeadershipMemberByUserEmail(email) || hasTeamType(teams, LEADERSHIP_PATTERN);
    }

    private boolean computeIsSupportEngineer(String email, ImmutableList<Team> teams) {
        return supportTeamService.isMemberByUserEmail(email) || hasTeamType(teams, SUPPORT_PATTERN);
    }

    private boolean computeIsEscalation(ImmutableList<Team> teams) {
        return hasTeamType(teams, ESCALATION_PATTERN);
    }

    private boolean hasTeamType(ImmutableList<Team> teams, Pattern pattern) {
        return teams.stream()
                .flatMap(t -> t.types().stream())
                .map(TeamType::name)
                .anyMatch(type -> pattern.matcher(type).find());
    }
}
