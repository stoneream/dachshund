package io.github.stoneream.dachshund.action

import io.github.stoneream.dachshund.action.TraceAction.TraceRequest
import io.github.stoneream.dachshund.http.TraceId
import org.apache.pekko.stream.testkit.NoMaterializer
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.should.Matchers
import play.api.mvc.{AnyContent, AnyContentAsEmpty, BodyParsers, Request, Results}
import play.api.test.FakeRequest
import play.api.test.Helpers.*

import scala.concurrent.{ExecutionContext, Future}

class TraceActionSpec extends AnyFeatureSpec with Matchers with Results {
  private given ExecutionContext = ExecutionContext.global
  private val traceAction: TraceAction =
    new TraceAction(new BodyParsers.Default()(NoMaterializer))

  Feature("TraceAction") {
    Scenario("既存の trace ID attribute を LoggingContext に保持する") {
      val traceId = "existing-trace-id"
      val request = fakeRequest().addAttr(TraceId.Attr, traceId)
      val result = traceIdResponse(request)

      status(result) shouldBe OK
      contentAsString(result) shouldBe traceId
    }

    Scenario("trace ID attribute がない場合は undefined trace ID を LoggingContext に設定する") {
      val result = traceIdResponse(fakeRequest())
      val traceId = contentAsString(result)

      status(result) shouldBe OK
      traceId shouldBe TraceId.Undefined
    }
  }

  private def traceIdResponse(request: Request[AnyContent]): Future[play.api.mvc.Result] = {
    val action = traceAction.async { (request: TraceRequest[AnyContent]) =>
      Future.successful(Ok(request.loggingContext.traceId))
    }

    action(request)
  }

  private def fakeRequest(): FakeRequest[AnyContent] =
    FakeRequest(GET, "/").withBody(AnyContentAsEmpty)
}
