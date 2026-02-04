package com.coreeng.supportbot.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

@Component
public class AuthCodeStore {
    private static final int codeLength = 32;
    private static final Duration codeExpiry = Duration.ofSeconds(60);

    private final Cache<String, String> codeToJwt = Caffeine.newBuilder()
        .expireAfterWrite(codeExpiry)
        .maximumSize(10_000)
        .build();
    private final SecureRandom secureRandom = new SecureRandom();

    public String storeToken(String jwt) {
        var code = generateCode();
        codeToJwt.put(code, jwt);
        return code;
    }

    public Optional<String> exchangeCode(String code) {
        var jwt = codeToJwt.getIfPresent(code);
        if (jwt != null) {
            codeToJwt.invalidate(code);
            return Optional.of(jwt);
        }
        return Optional.empty();
    }

    private String generateCode() {
        var bytes = new byte[codeLength];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
