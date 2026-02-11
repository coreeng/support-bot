package com.coreeng.supportbot.security;

import com.coreeng.supportbot.teams.TeamType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthCodeStore authCodeStore;
    private final OAuthUrlService oauthUrlService;
    private final OAuthExchangeService oauthExchangeService;

    @PostMapping("/token")
    public ResponseEntity<TokenResponse> exchangeToken(@RequestBody TokenRequest request) {
        var jwtOpt = authCodeStore.exchangeCode(request.code());
        if (jwtOpt.isEmpty()) {
            log.warn("Invalid or expired auth code");
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(new TokenResponse(jwtOpt.get()));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }

        var teams = principal.teams().stream()
                .map(t -> new TeamResponse(
                        t.label(),
                        t.code(),
                        t.types().stream().map(TeamType::name).toList()))
                .toList();

        var roles = principal.roles().stream().map(Enum::name).toList();

        return ResponseEntity.ok(new UserResponse(principal.email(), principal.name(), teams, roles));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal != null) {
            log.info("User logged out");
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/oauth-url")
    public ResponseEntity<OAuthUrlResponse> getOAuthUrl(
            @RequestParam String provider, @RequestParam String redirectUri) {
        var authUrlOpt = oauthUrlService.getAuthorizationUrl(provider, redirectUri);
        if (authUrlOpt.isEmpty()) {
            log.warn("Invalid OAuth provider: {}", provider);
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(new OAuthUrlResponse(authUrlOpt.get()));
    }

    @PostMapping("/oauth/exchange")
    public ResponseEntity<TokenResponse> exchangeOAuthCode(@RequestBody OAuthExchangeRequest request) {
        try {
            var jwt = oauthExchangeService.exchangeCodeForToken(
                    request.provider(), request.code(), request.redirectUri());
            return ResponseEntity.ok(new TokenResponse(jwt));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid OAuth provider: {}", request.provider());
            return ResponseEntity.badRequest().build();
        } catch (IllegalStateException e) {
            log.error("OAuth exchange failed", e);
            return ResponseEntity.status(500).build();
        }
    }

    public record TokenRequest(String code) {}

    public record TokenResponse(String token) {}

    public record UserResponse(String email, String name, List<TeamResponse> teams, List<String> roles) {}

    public record TeamResponse(String label, String code, List<String> types) {}

    public record OAuthUrlResponse(String url) {}

    public record OAuthExchangeRequest(String provider, String code, String redirectUri) {}
}
