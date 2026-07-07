package com.coreeng.supportbot;

import static org.assertj.core.api.Assertions.assertThat;

import com.coreeng.supportbot.testkit.SupportBotClient;
import com.coreeng.supportbot.testkit.TestKitExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Exercises the real Spring Security filter chain for the export endpoints — something
 * {@code SummaryExportControllerTest} (a unit test constructing the controller directly) cannot
 * cover, since it bypasses security entirely.
 */
@ExtendWith(TestKitExtension.class)
public class SummaryExportControllerFunctionalTests {

    private SupportBotClient supportBotClient;

    // TestAuthBypassFilter maps the "leadership" test role to BOTH Role.LEADERSHIP and
    // Role.SUPPORT_ENGINEER (TestAuthBypassFilter.java:46: isSupportEngineer is true whenever
    // isLeadership is true), so it can't simulate a leadership-only principal. "escalation" grants
    // only Role.ESCALATION + Role.USER — no SUPPORT_ENGINEER — making it a valid stand-in for "any
    // authenticated user without SUPPORT_ENGINEER" when proving the /summary-data/** security rule
    // (SecurityConfig.java) is enforced end-to-end, not just assumed.
    private static final String NON_SUPPORT_ENGINEER_ROLE = "escalation";

    // SecurityConfig's custom authenticationEntryPoint fires for BOTH missing authentication and
    // insufficient role (confirmed empirically: an authenticated-but-wrong-role request gets the
    // exact same {"error":"Unauthorized"} 401 body as a fully anonymous one) — there's no separate
    // 403/AccessDeniedHandler path in this app, so 401 is the correct expectation here, not 403.
    @Test
    void start_returns401_forNonSupportEngineerRole() {
        int statusCode =
                supportBotClient.postStatusCodeAsRole("/summary-data/export/start?days=7", NON_SUPPORT_ENGINEER_ROLE);

        assertThat(statusCode).isEqualTo(401);
    }

    @Test
    void status_returns401_forNonSupportEngineerRole() {
        int statusCode = supportBotClient.getStatusCodeAsRole("/summary-data/export/status", NON_SUPPORT_ENGINEER_ROLE);

        assertThat(statusCode).isEqualTo(401);
    }

    @Test
    void download_returns401_forNonSupportEngineerRole() {
        int statusCode =
                supportBotClient.getStatusCodeAsRole("/summary-data/export/download", NON_SUPPORT_ENGINEER_ROLE);

        assertThat(statusCode).isEqualTo(401);
    }

    @Test
    void status_returns200_forSupportEngineerRole() {
        assertThat(supportBotClient.export().status()).isNotNull();
    }

    @Test
    void download_returns404_forSupportEngineerRole_whenNothingReady() {
        assertThat(supportBotClient.export().downloadStatusCode()).isEqualTo(404);
    }
}
