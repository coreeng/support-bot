package com.coreeng.supportbot.nft

import com.coreeng.supportbot.testkit._
import org.slf4j.LoggerFactory

import java.util.concurrent.Executors

/**
 * Singleton holding TestKit components, initialized once per JVM.
 */
object TestKitHolder {
  private val logger = LoggerFactory.getLogger(getClass)
  private val CONFIG_FILE = "config.yaml"

  lazy val testKit: TestKit = initialize()
  lazy val virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor()

  def slackWiremock: SlackWiremock = testKit.slack().wiremock()

  private def initialize(): TestKit = {
    logger.info("Initializing TestKit for NFT...")

    val config = Config.load(CONFIG_FILE)
    val testKit = TestKit.create(config)

    testKit.slack().wiremock().start()
    testKit.slack().wiremock().permanent().setupAllNftStubs()
    logger.info("SlackWiremock started on port {}", testKit.slack().wiremock().port())

    testKit
  }

  def shutdown(): Unit = {
    testKit.slack().wiremock().stop()
    virtualThreadExecutor.shutdown()
    logger.info("TestKit shutdown complete")
  }

  Runtime.getRuntime.addShutdownHook(new Thread(() => shutdown()))
}
