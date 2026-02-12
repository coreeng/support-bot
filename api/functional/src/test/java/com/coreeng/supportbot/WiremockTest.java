package com.coreeng.supportbot;

import static io.restassured.RestAssured.given;

import com.coreeng.supportbot.testkit.SlackWiremock;
import com.coreeng.supportbot.testkit.TestKitExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Test class to verify that the Slack Wiremock server is running correctly.
 */
@ExtendWith(TestKitExtension.class)
public class WiremockTest {
    // This field will be injected by TestKitExtension based on their types
    private SlackWiremock slackWiremock;

    @Test
    public void testWiremockServersAreRunning() {
        given().when().get("http://localhost:8000/__admin").then().assertThat().statusCode(200);
    }
}
