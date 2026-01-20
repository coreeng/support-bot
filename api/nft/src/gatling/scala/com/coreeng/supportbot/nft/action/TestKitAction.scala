package com.coreeng.supportbot.nft.action

import com.coreeng.supportbot.nft.TestKitHolder
import com.coreeng.supportbot.testkit.TestKit
import io.gatling.commons.stats.{KO, OK}
import io.gatling.commons.util.Clock
import io.gatling.core.action.Action
import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.session.Session
import io.gatling.core.stats.StatsEngine
import io.gatling.core.structure.ScenarioContext

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object TestKitExecutor {
  implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(TestKitHolder.virtualThreadExecutor)
}

class TestKitActionBuilder(
    requestName: String,
    testKit: TestKit,
    step: (TestKit, Session) => Session
) extends ActionBuilder {

  override def build(ctx: ScenarioContext, next: Action): Action = {
    new TestKitAction(requestName, testKit, step, ctx.coreComponents.statsEngine, ctx.coreComponents.clock, next)
  }
}

class TestKitAction(
    requestName: String,
    testKit: TestKit,
    step: (TestKit, Session) => Session,
    statsEngine: StatsEngine,
    clock: Clock,
    next: Action
) extends Action {

  import TestKitExecutor._

  override def name: String = requestName

  override def execute(session: Session): Unit = {
    val startTimestamp = clock.nowMillis

    Future {
      step(testKit, session)
    }.onComplete {
      case Success(updatedSession) =>
        val endTimestamp = clock.nowMillis
        statsEngine.logResponse(session.scenario, session.groups, requestName, startTimestamp, endTimestamp, OK, None, None)
        next ! updatedSession

      case Failure(exception) =>
        val endTimestamp = clock.nowMillis
        statsEngine.logResponse(session.scenario, session.groups, requestName, startTimestamp, endTimestamp, KO, None, Some(exception.getMessage))
        next ! session.markAsFailed
    }
  }
}

object TestKitDsl {
  def testKitExec(requestName: String, testKit: TestKit)(
    step: (TestKit, Session) => Session
  ): ActionBuilder = {
    new TestKitActionBuilder(requestName, testKit, step)
  }
}
