package com.coreeng.supportbot.nft

import com.coreeng.supportbot.testkit._
import com.google.common.collect.ImmutableList
import io.gatling.core.Predef._
import io.gatling.core.session.Session

import java.time.Duration
import java.util.concurrent.ThreadLocalRandom
import scala.concurrent.duration._

class TicketFlowSimulation extends Simulation {

  private val testKit = TestKitHolder.testKit
  private val slackWiremock = TestKitHolder.slackWiremock
  
  import action.TestKitDsl._

  private val ticketFlowScenario = scenario("Slack Ticket Flow")
    .exec(testKitExec("post-tenant-message", testKit)(postTenantMessage))
    .pause(800.milliseconds, 1200.milliseconds)
    .exec(testKitExec("create-ticket", testKit)(createTicket))
    .pause(800.milliseconds, 1200.milliseconds)
    .exec(testKitExec("open-summary", testKit)(openSummaryModal))
    .pause(800.milliseconds, 1200.milliseconds)
    .exec(testKitExec("submit-summary", testKit)(submitSummaryForm))

  setUp(
    ticketFlowScenario.inject(constantUsersPerSec(10).during(10.minutes).randomized)
  ).assertions(
    global.failedRequests.count.is(0),
    details("post-tenant-message").responseTime.percentile(95).lt(100),
    details("create-ticket").responseTime.percentile(95).lt(700),
    details("open-summary").responseTime.percentile(95).lt(600),
    details("submit-summary").responseTime.percentile(95).lt(1500)
  )

  /**
   * Step 1: Tenant posts a message to the support channel.
   */
  private def postTenantMessage(tk: TestKit, session: Session): Session = {
    val asTenant = tk.as(UserRole.tenant)
    val queryTs = MessageTs.now()
    val queryText = s"NFT test query ${System.currentTimeMillis()}"

    val queryMessage = asTenant.slack().postMessage(queryTs, queryText)

    session
      .set("queryMessage", queryMessage)
  }

  /**
   * Step 2: Support reacts with eyes emoji to create a ticket.
   * Triggers ticket creation and waits until the ticket form posted.
   */
  private def createTicket(tk: TestKit, session: Session): Session = {
    val asSupport = tk.as(UserRole.support)
    val queryMessage = session("queryMessage").as[SlackMessage]

    asSupport.slack().addReactionTo(queryMessage, "eyes")

    val result = RequestJournalVerifier.awaitTicketFormAndExtractId(
      slackWiremock,
      queryMessage.ts().toString,
      Duration.ofSeconds(5)
    )

    session
      .set("ticketId", result.ticketId)
      .set("formMessageTs", result.formMessageTs)
  }

  /**
   * Step 3: Support clicks the "Full Summary" button to open the summary modal.
   */
  private def openSummaryModal(tk: TestKit, session: Session): Session = {
    val asSupport = tk.as(UserRole.support)
    val ticketId = session("ticketId").as[Long]
    val triggerId = s"nft_trigger_${System.currentTimeMillis()}_${ThreadLocalRandom.current().nextInt(100000)}"

    asSupport.slack().clickMessageButton(
      FullSummaryButtonClick.builder()
        .triggerId(triggerId)
        .ticketId(ticketId)
        .build()
    )

    RequestJournalVerifier.awaitViewsOpenWithTriggerId(
      slackWiremock,
      triggerId,
      Duration.ofSeconds(5)
    )

    session
  }

  /**
   * Step 4: Support submits the summary form.
   */
  private def submitSummaryForm(tk: TestKit, session: Session): Session = {
    val asSupport = tk.as(UserRole.support)
    val ticketId = session("ticketId").as[Long]
    val formMessageTs = session("formMessageTs").as[String]
    val triggerId = s"nft_submit_${System.currentTimeMillis()}_${ThreadLocalRandom.current().nextInt(100000)}"

    asSupport.slack().submitView(
      FullSummaryFormSubmission.builder()
        .triggerId(triggerId)
        .ticketId(ticketId)
        .values(FullSummaryFormSubmission.Values.builder()
          .status(Ticket.Status.closed)
          .team("connected-app")
          .tags(ImmutableList.of("networking"))
          .impact("productionBlocking")
          .build())
        .build()
    )

    RequestJournalVerifier.awaitChatUpdate(
      slackWiremock,
      formMessageTs,
      Duration.ofSeconds(5)
    )

    session
  }
}
