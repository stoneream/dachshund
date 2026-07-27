package io.github.stoneream.dachshund.controller.lib

import io.github.stoneream.dachshund.action.TraceAction.TraceRequest
import io.github.stoneream.dachshund.handler.lib.HandlerBase
import io.github.stoneream.dachshund.handler.lib.PageMeta
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import play.api.mvc.{Result, Results}

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.Try

trait ControllerBaseImpl {
  final def handle[
      Request <: TraceRequest[_],
      UseCaseInput,
      UseCaseOutput,
      UseCaseException: ClassTag,
      Response
  ](
      handler: HandlerBase[
        Request,
        UseCaseInput,
        UseCaseOutput,
        UseCaseException,
        Response
      ]
  )(request: Request)(using ExecutionContext): Future[Result] = {
    val renderer = handler.renderer
    given LoggingContext = request.loggingContext

    Future
      .fromTry(Try(handler.execute(request)))
      .flatten
      .map(output => renderer.success(output, request))
      .recover {
        case ControllerError.LoginRequired =>
          Results
            .SeeOther("/spotify/auth/login")
            .withHeaders(PageMeta.XRobotsTagHeaderName -> PageMeta.NoIndexNoFollow)
        case ex: ControllerError if ex.isServerError =>
          Results
            .InternalServerError(views.html.global_http_error.internal_server_error(request.path))
            .withHeaders(PageMeta.XRobotsTagHeaderName -> PageMeta.NoIndexNoFollow)
        case ex: ControllerError =>
          Results
            .Status(ex.statusCode)(
              views.html.global_http_error.bad_request(
                request.path,
                ex.detail.orElse(ex.cause).getOrElse(ex.title)
              )
            )
            .withHeaders(PageMeta.XRobotsTagHeaderName -> PageMeta.NoIndexNoFollow)
        case ex: UseCaseException => renderer.failure(ex, request)
        case e => throw e
      }
  }
}
