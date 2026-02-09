package com.coreeng.supportbot.security;

import com.coreeng.supportbot.teams.TeamType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthCodeStore authCodeStore;

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
                t.types().stream().map(TeamType::name).toList()
            ))
            .toList();

        return ResponseEntity.ok(new UserResponse(
            principal.email(),
            principal.name(),
            teams,
            principal.isLeadership(),
            principal.isSupportEngineer(),
            principal.isEscalation()
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal != null) {
            log.info("User logged out");
        }
        return ResponseEntity.ok().build();
    }

    public record TokenRequest(String code) {}

    public record TokenResponse(String token) {}

    public record UserResponse(
        String email,
        String name,
        List<TeamResponse> teams,
        boolean isLeadership,
        boolean isSupportEngineer,
        boolean isEscalation
    ) {}

    public record TeamResponse(
        String label,
        String code,
        List<String> types
    ) {}
}
