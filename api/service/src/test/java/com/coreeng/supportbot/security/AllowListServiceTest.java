package com.coreeng.supportbot.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class AllowListServiceTest {

    private AllowListService createService(List<String> emails, List<String> domains) {
        var props = new SecurityProperties(
                new SecurityProperties.JwtProperties(
                        "unused-secret-that-is-long-enough-for-256-bits", Duration.ofHours(1)),
                new SecurityProperties.OAuth2Properties("http://localhost:3000/login"),
                new SecurityProperties.CorsProperties(null),
                new SecurityProperties.TestBypassProperties(false),
                new SecurityProperties.AllowListProperties(emails, domains));
        return new AllowListService(props);
    }

    @Test
    void emptyLists_allowsEveryone() {
        var service = createService(List.of(), List.of());
        assertTrue(service.isAllowed("anyone@anywhere.com"));
    }

    @Test
    void defaultEmptyLists_allowsEveryone() {
        var service = createService(List.of(), List.of());
        assertTrue(service.isAllowed("anyone@anywhere.com"));
    }

    @Test
    void emailInList_allowed() {
        var service = createService(List.of("alice@example.com"), List.of());
        assertTrue(service.isAllowed("alice@example.com"));
    }

    @Test
    void emailNotInList_rejected() {
        var service = createService(List.of("alice@example.com"), List.of());
        assertFalse(service.isAllowed("bob@example.com"));
    }

    @Test
    void domainInList_allowed() {
        var service = createService(List.of(), List.of("example.com"));
        assertTrue(service.isAllowed("anyone@example.com"));
    }

    @Test
    void domainNotInList_rejected() {
        var service = createService(List.of(), List.of("example.com"));
        assertFalse(service.isAllowed("anyone@other.com"));
    }

    @Test
    void emailMatchTakesPriorityOverMissingDomain() {
        var service = createService(List.of("special@other.com"), List.of("example.com"));
        assertTrue(service.isAllowed("special@other.com"));
    }

    @Test
    void caseInsensitive_email() {
        var service = createService(List.of("Alice@Example.COM"), List.of());
        assertTrue(service.isAllowed("alice@example.com"));
    }

    @Test
    void caseInsensitive_domain() {
        var service = createService(List.of(), List.of("Example.COM"));
        assertTrue(service.isAllowed("user@example.com"));
    }

    @Test
    void whitespaceInConfig_trimmed() {
        var service = createService(List.of("  alice@example.com  "), List.of("  corp.io  "));
        assertTrue(service.isAllowed("alice@example.com"));
        assertTrue(service.isAllowed("bob@corp.io"));
    }

    @Test
    void blankEntries_ignored() {
        var service = createService(List.of("", "  ", "alice@example.com"), List.of());
        assertTrue(service.isAllowed("alice@example.com"));
        // Blank entries should not cause empty-list = allow-all behavior
        assertFalse(service.isAllowed("bob@other.com"));
    }

    @Test
    void onlyDomainsConfigured_emailListEffectivelyEmpty() {
        var service = createService(List.of(), List.of("allowed.com"));
        assertTrue(service.isAllowed("anyone@allowed.com"));
        assertFalse(service.isAllowed("anyone@blocked.com"));
    }

    @Test
    void onlyEmailsConfigured_domainListEffectivelyEmpty() {
        var service = createService(List.of("one@specific.com"), List.of());
        assertTrue(service.isAllowed("one@specific.com"));
        assertFalse(service.isAllowed("two@specific.com"));
    }
}
