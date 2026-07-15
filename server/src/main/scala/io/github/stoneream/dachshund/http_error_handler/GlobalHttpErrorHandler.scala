package io.github.stoneream.dachshund.http_error_handler

import io.github.stoneream.dachshund.logging.Logger
import play.api.http.HttpErrorHandler
import play.api.http.{Status => HttpStatus}
import play.api.mvc.{RequestHeader, Result, Results}

import com.google.inject.Singleton
import scala.concurrent.Future

@Singleton
class GlobalHttpErrorHandler extends HttpErrorHandler with Results with Logger {
  override def onClientError(
      request: RequestHeader,
      statusCode: Int,
      message: String
  ): Future[Result] = {
    logger.info(
      "HTTP クライアントエラーが発生しました",
      kv("status", statusCode),
      kv("method", request.method),
      kv("path", request.path),
      kv("message", message)
    )

    val result = statusCode match {
      case HttpStatus.BAD_REQUEST =>
        BadRequest(views.html.global_http_error.bad_request(request.path, message))
      case HttpStatus.NOT_FOUND =>
        NotFound(views.html.global_http_error.not_found(request.path))
      case _ =>
        Status(statusCode)("Client Error")
    }

    Future.successful(result)
  }

  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {
    logger.error(
      "HTTP サーバーエラーが発生しました",
      kv("method", request.method),
      kv("path", request.path),
      kv("exceptionClass", exception.getClass.getName),
      exception
    )
    Future.successful(InternalServerError(views.html.global_http_error.internal_server_error(request.path)))
  }
}
