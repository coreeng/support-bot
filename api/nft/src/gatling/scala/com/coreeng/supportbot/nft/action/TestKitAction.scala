package com.coreeng.supportbot.nft.action

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

class TestKitActionBuilder(
    requestName: String,
    testKit: TestKit,
    executionContext: ExecutionContext,
    step: (TestKit, Session) => Session
) extends ActionBuilder {

  override def build(ctx: ScenarioContext, next: Action): Action = {
    new TestKitAction(requestName, testKit, executionContext, step, ctx.coreComponents.statsEngine, ctx.coreComponents.clock, next)
  }
}

class TestKitAction(
    requestName: String,
    testKit: TestKit,
    executionContext: ExecutionContext,
    step: (TestKit, Session) => Session,
    statsEngine: StatsEngine,
    clock: Clock,
    next: Action
) extends Action {

  override def name: String = requestName

  override def execute(session: Session): Unit = {
    val startTimestamp = clock.nowMillis

    Future {
      step(testKit, session)
    }(executionContext).onComplete {
      case Success(updatedSession) =>
        val endTimestamp = clock.nowMillis
        statsEngine.logResponse(session.scenario, session.groups, requestName, startTimestamp, endTimestamp, OK, None, None)
        next ! updatedSession

      case Failure(exception) =>
        val endTimestamp = clock.nowMillis
        val failedSession = session.markAsFailed
        statsEngine.logResponse(
          failedSession.scenario,
          failedSession.groups,
          requestName,
          startTimestamp,
          endTimestamp,
          KO,
          None,
          Option(exception.getMessage)
        )

        next ! failedSession
    }(executionContext)
  }
}

object TestKitDsl {
  def testKitExec(requestName: String, executionContext: ExecutionContext, testKit: TestKit)(
    step: (TestKit, Session) => Session
  ): ActionBuilder = {
    new TestKitActionBuilder(requestName, testKit, executionContext, step)
  }
}
