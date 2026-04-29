package com.coreeng.supportbot.nft

import com.coreeng.supportbot.testkit.{SlackWiremock, StubWithResult}
import com.github.tomakehurst.wiremock.client.WireMock._
import org.awaitility.Awaitility

import java.time.Duration

/**
 * Verifies Slack API calls and ticket creation progress during NFT runs.
 */
object RequestJournalVerifier {
  private val defaultTimeout: Duration = Duration.ofSeconds(30)
  private val pollInterval = Duration.ofMillis(10)

  def awaitStubCalled[T](stub: StubWithResult[T], timeout: Duration = defaultTimeout): T = {
    Awaitility.await()
      .atMost(timeout)
      .pollInterval(pollInterval)
      .untilAsserted(() => stub.assertIsCalled())
    stub.result()
  }

  def awaitViewsOpenWithTriggerId(wiremock: SlackWiremock, triggerId: String, timeout: Duration = defaultTimeout): Unit = {
    Awaitility.await()
      .atMost(timeout)
      .pollInterval(pollInterval)
      .untilAsserted { () =>
        val requests = wiremock.findAll(
          postRequestedFor(urlPathEqualTo("/api/views.open"))
            .withRequestBody(containing(triggerId))
        )
        if (requests.isEmpty) {
          throw new AssertionError(s"No views.open requests found containing triggerId '$triggerId'")
        }
      }
  }
}
