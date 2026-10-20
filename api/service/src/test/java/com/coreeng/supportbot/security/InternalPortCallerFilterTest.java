package com.coreeng.supportbot.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class InternalPortCallerFilterTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesTheCallerAsAServicePrincipalHoldingOnlyTheServiceAuthority() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/erasure-requests");
        request.setRemoteAddr("10.4.2.17");
        MockFilterChain chain = new MockFilterChain();

        new InternalPortCallerFilter().doFilter(request, new MockHttpServletResponse(), chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isInstanceOf(ServicePrincipalAuthenticationToken.class);
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal()).isEqualTo(new ServicePrincipal("10.4.2.17"));
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("elevate:principal:service");
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void noRoleGrantsTheServiceAuthority() {
        assertThat(Role.values())
                .extracting(Role::authority)
                .doesNotContain(ServicePrincipalAuthenticationToken.AUTHORITY);
    }
}
