package com.coreeng.supportbot.nft

import com.coreeng.supportbot.testkit.{SlackWiremock, TicketMessage}
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.github.tomakehurst.wiremock.client.WireMock._
import org.awaitility.Awaitility

import java.time.Duration
import scala.jdk.CollectionConverters._

/**
 * Verifies Slack API calls via WireMock's RequestJournal.
 */
object RequestJournalVerifier {
  private val defaultTimeout: Duration = Duration.ofSeconds(30)
  private val pollInterval = Duration.ofMillis(10)
  private val objectMapper = new ObjectMapper()

  /**
   * Waits for a chat.update call containing the specified formMessageTs.
   * Uses the unique message timestamp from the ticket form response to avoid
   * false positives from substring matching on numeric ticket IDs.
   */
  def awaitChatUpdate(wiremock: SlackWiremock, formMessageTs: String, timeout: Duration = defaultTimeout): Unit = {
    Awaitility.await()
      .atMost(timeout)
      .pollInterval(pollInterval)
      .untilAsserted { () =>
        val requests = wiremock.findAll(
          postRequestedFor(urlPathEqualTo("/api/chat.update"))
            .withFormParam("ts", equalTo(formMessageTs))
        )
        if (requests.isEmpty) {
          throw new AssertionError(s"No chat.update requests found with ts='$formMessageTs'")
        }
      }
  }

  case class TicketFormResult(ticketId: Long, formMessageTs: String)

  /**
   * Waits for a chat.postMessage call and extracts both the ticket ID from the request
   * and the generated message timestamp from the response.
   */
  def awaitTicketFormAndExtractId(wiremock: SlackWiremock, uniqueId: String, timeout: Duration = defaultTimeout): TicketFormResult = {
    val receiver = new TicketMessage.Receiver()
    var ticketId: Long = -1
    var formMessageTs: String = ""

    Awaitility.await()
      .atMost(timeout)
      .pollInterval(pollInterval)
      .untilAsserted { () =>
        // Use getAllServeEvents to get both request and response
        val allEvents = wiremock.getAllServeEvents.asScala
        val matchingEvent = allEvents.find { event =>
          val req = event.getRequest
          req.getUrl == "/api/chat.postMessage" &&
            req.getBodyAsString.contains(uniqueId)
        }.getOrElse {
          throw new AssertionError(s"No chat.postMessage requests found containing '$uniqueId'")
        }

        // Extract ticketId from request
        val request = matchingEvent.getRequest
        val textParam = request.formParameter("text")
        if (!textParam.isPresent) {
          throw new IllegalStateException("No 'text' form parameter found in request")
        }
        try {
          ticketId = receiver.extractTicketIdFromText(textParam.firstValue())
        } catch {
          case e: AssertionError =>
            throw new IllegalStateException("Failed to parse ticketId from 'text' form parameter", e)
        }

        // Extract formMessageTs from response
        val responseBody = matchingEvent.getResponse.getBodyAsString
        val jsonNode: JsonNode = objectMapper.readTree(responseBody)
        val tsNode = jsonNode.get("ts")
        if (tsNode == null) {
          throw new IllegalStateException("No 'ts' field found in response body JSON")
        }
        formMessageTs = tsNode.asText()
        if (formMessageTs.isEmpty) {
          throw new IllegalStateException("Empty 'ts' field in response body JSON")
        }
      }

    TicketFormResult(ticketId, formMessageTs)
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
