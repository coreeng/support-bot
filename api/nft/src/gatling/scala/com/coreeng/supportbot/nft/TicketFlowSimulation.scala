package com.coreeng.supportbot.nft

import com.coreeng.supportbot.testkit._
import com.google.common.collect.ImmutableList
import io.gatling.core.Predef._
import io.gatling.core.session.Session
import org.slf4j.LoggerFactory

import java.util.concurrent.{ExecutorService, Executors, ThreadLocalRandom, TimeUnit}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.control.NonFatal

class TicketFlowSimulation extends Simulation {
  private val logger = LoggerFactory.getLogger(getClass)

  private val config: Config = Config.load("config.yaml")
  private val testKit: TestKit = TestKit.create(config)
  private val slackWiremock: SlackWiremock = testKit.slack().wiremock()
  private val executorService: ExecutorService = Executors.newVirtualThreadPerTaskExecutor()
  private implicit val executionContext: ExecutionContext = ExecutionContext.fromExecutor(executorService)

  import action.TestKitDsl._

  private val ticketFlowScenario = scenario("Slack Ticket Flow")
    .exec(testKitExec("post-tenant-message", executionContext, testKit)(postTenantMessage))
    .exitHereIfFailed
    .pause(800.milliseconds, 1200.milliseconds)
    .exec(testKitExec("create-ticket", executionContext, testKit)(createTicket))
    .exitHereIfFailed
    .pause(800.milliseconds, 1200.milliseconds)
    .exec(testKitExec("open-summary", executionContext, testKit)(openSummaryModal))
    .exitHereIfFailed
    .pause(800.milliseconds, 1200.milliseconds)
    .exec(testKitExec("submit-summary", executionContext, testKit)(submitSummaryForm))
    .exitHereIfFailed

  setUp(
    ticketFlowScenario.inject(
      // Warmup: low traffic for 1 minute to let the service, JVM and mocks
      // stabilise before the main load.
      constantUsersPerSec(0.5).during(1.minute).randomized,
      // Main load: the original profile.
      constantUsersPerSec(5).during(1.minute).randomized
    )
  ).assertions(
    global.failedRequests.count.is(0),
    details("post-tenant-message").responseTime.percentile(50).lt(5000),
    details("create-ticket").responseTime.percentile(50).lt(5000),
    details("open-summary").responseTime.percentile(50).lt(5000),
    details("submit-summary").responseTime.percentile(50).lt(5000)
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
    )

    session
  }

  before {
    logger.info("Initializing TestKit for NFT...")
    slackWiremock.start()
    slackWiremock.permanent().setupAllNftStubs()
    logger.info("SlackWiremock started on port {}", slackWiremock.port())
  }

  after {
    try {
      testKit.slack().wiremock().stop()
    } catch {
      case NonFatal(e) =>
        logger.warn("Error while stopping SlackWiremock in NFT after hook", e)
    }

    executorService.shutdown()

    try {
      if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
        logger.warn("ExecutorService did not terminate within 10 seconds")
      }
    } catch {
      case NonFatal(e) =>
        logger.warn("Interrupted or failed while awaiting executor termination", e)
    }
  }
}
