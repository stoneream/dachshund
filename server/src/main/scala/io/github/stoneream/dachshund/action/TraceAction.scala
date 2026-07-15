package io.github.stoneream.dachshund.action

import com.google.inject.Inject
import io.github.stoneream.dachshund.http.TraceId
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import play.api.mvc.*

import scala.concurrent.{ExecutionContext, Future}

class TraceAction @Inject() (
    val parser: BodyParsers.Default
)(using ec: ExecutionContext)
    extends ActionBuilder[TraceAction.TraceRequest, AnyContent] {

  override protected def executionContext: ExecutionContext = ec

  override def invokeBlock[A](
      request: Request[A],
      block: TraceAction.TraceRequest[A] => Future[Result]
  ): Future[Result] = {
    val traceId =
      request.attrs.get(TraceId.Attr).getOrElse(TraceId.Undefined)

    block(TraceAction.TraceRequest(LoggingContext(traceId), request))
  }
}

object TraceAction {
  final case class TraceRequest[A](
      loggingContext: LoggingContext,
      request: Request[A]
  ) extends WrappedRequest[A](request)
}
