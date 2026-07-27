package io.github.stoneream.dachshund.action

import io.github.stoneream.dachshund.action.TraceAction.TraceRequest
import io.github.stoneream.dachshund.auth.UserSessionContext
import io.github.stoneream.dachshund.http.TraceId
import io.github.stoneream.dachshund.lib.datetime.{BusinessDateTime, DateTimeService}
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import org.apache.pekko.stream.testkit.NoMaterializer
import org.mockito.scalatest.IdiomaticMockito
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.should.Matchers
import play.api.mvc.{AnyContent, AnyContentAsEmpty, BodyParsers, Request, RequestHeader, Results}
import play.api.test.FakeRequest
import play.api.test.Helpers.*

import scala.concurrent.{ExecutionContext, Future}

class TraceActionSpec extends AnyFeatureSpec with Matchers with Results with IdiomaticMockito {
  private given ExecutionContext = ExecutionContext.global
  private val fixedNow = BusinessDateTime.from("2026-07-01T12:00:00+09:00")
  private val dateTimeService = mock[DateTimeService]
  private val userContextResolver = mock[UserContextResolver]
  private val traceAction: TraceAction =
    new TraceAction(new BodyParsers.Default()(NoMaterializer), dateTimeService, userContextResolver)

  dateTimeService.now() returns fixedNow

  Feature("TraceAction") {
    Scenario("既存の trace ID attribute を LoggingContext に保持する") {
      userContextResolver.resolve(*[RequestHeader], fixedNow)(using *[LoggingContext]) returns Future.successful(UserSessionContext.NotLoggedIn)
      val traceId = "existing-trace-id"
      val request = fakeRequest().addAttr(TraceId.Attr, traceId)
      val result = traceIdResponse(request)

      status(result) shouldBe OK
      contentAsString(result) shouldBe traceId
    }

    Scenario("trace ID attribute がない場合は undefined trace ID を LoggingContext に設定する") {
      userContextResolver.resolve(*[RequestHeader], fixedNow)(using *[LoggingContext]) returns Future.successful(UserSessionContext.NotLoggedIn)
      val result = traceIdResponse(fakeRequest())
      val traceId = contentAsString(result)

      status(result) shouldBe OK
      traceId shouldBe TraceId.Undefined
    }

    Scenario("解決したユーザーコンテキストを TraceRequest に保持する") {
      val user = UserSessionContext.NormalUser(
        userId = 1L,
        userName = "normal-user",
        displayName = "Normal User"
      )
      userContextResolver.resolve(*[RequestHeader], fixedNow)(using *[LoggingContext]) returns Future.successful(user)

      val result = userContextResponse(fakeRequest())

      status(result) shouldBe OK
      contentAsString(result) shouldBe "1:Normal User"
    }
  }

  private def traceIdResponse(request: Request[AnyContent]): Future[play.api.mvc.Result] = {
    val action = traceAction.async { (request: TraceRequest[AnyContent]) =>
      Future.successful(Ok(request.loggingContext.traceId))
    }

    action(request)
  }

  private def userContextResponse(request: Request[AnyContent]): Future[play.api.mvc.Result] = {
    val action = traceAction.async { (request: TraceRequest[AnyContent]) =>
      val label = request.userSessionContext match {
        case UserSessionContext.NotLoggedIn => "not-logged-in"
        case user: UserSessionContext.NormalUser => s"${user.userId}:${user.displayName}"
      }
      Future.successful(Ok(label))
    }

    action(request)
  }

  private def fakeRequest(): FakeRequest[AnyContent] =
    FakeRequest(GET, "/").withBody(AnyContentAsEmpty)
}
