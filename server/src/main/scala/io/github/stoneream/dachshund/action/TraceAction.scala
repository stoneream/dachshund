package io.github.stoneream.dachshund.action

import com.google.inject.Inject
import io.github.stoneream.dachshund.auth.UserSessionContext
import io.github.stoneream.dachshund.http.TraceId
import io.github.stoneream.dachshund.lib.datetime.DateTimeService
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import play.api.mvc.*

import scala.concurrent.{ExecutionContext, Future}

class TraceAction @Inject() (
    val parser: BodyParsers.Default,
    dateTimeService: DateTimeService,
    userContextResolver: UserContextResolver
)(using ec: ExecutionContext)
    extends ActionBuilder[TraceAction.TraceRequest, AnyContent] {

  override protected def executionContext: ExecutionContext = ec

  override def invokeBlock[A](
      request: Request[A],
      block: TraceAction.TraceRequest[A] => Future[Result]
  ): Future[Result] = {
    val traceId =
      request.attrs.get(TraceId.Attr).getOrElse(TraceId.Undefined)
    val loggingContext = LoggingContext(traceId)
    given LoggingContext = loggingContext

    userContextResolver
      .resolve(request, dateTimeService.now())
      .flatMap { userSessionContext =>
        block(TraceAction.TraceRequest(loggingContext, userSessionContext, request))
      }
  }
}

object TraceAction {
  final case class TraceRequest[A](
      loggingContext: LoggingContext,
      userSessionContext: UserSessionContext,
      request: Request[A]
  ) extends WrappedRequest[A](request)
}
