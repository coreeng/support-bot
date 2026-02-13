package com.coreeng.supportbot.security;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AllowListService {
    private final Set<String> emails;
    private final Set<String> domains;

    public AllowListService(SecurityProperties properties) {
        this.emails = properties.allowList().emails().stream()
                .map(e -> e.toLowerCase(Locale.ROOT).trim())
                .filter(e -> !e.isBlank())
                .collect(Collectors.toUnmodifiableSet());
        this.domains = properties.allowList().domains().stream()
                .map(d -> d.toLowerCase(Locale.ROOT).trim())
                .filter(d -> !d.isBlank())
                .collect(Collectors.toUnmodifiableSet());
        if (!emails.isEmpty() || !domains.isEmpty()) {
            log.info("Allow-list active: {} emails, {} domains", emails.size(), domains.size());
        }
    }

    public boolean isAllowed(String email) {
        if (emails.isEmpty() && domains.isEmpty()) {
            return true;
        }
        if (emails.contains(email)) {
            return true;
        }
        var domain = email.substring(email.indexOf('@') + 1);
        return domains.contains(domain);
    }
}
