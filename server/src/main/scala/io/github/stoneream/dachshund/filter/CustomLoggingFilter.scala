package io.github.stoneream.dachshund.filter

import io.github.stoneream.dachshund.http.TraceId
import io.github.stoneream.dachshund.logging.Logger
import org.apache.pekko.stream.Materializer
import play.api.mvc.{Filter, RequestHeader, Result}

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import com.google.inject.{Inject, Singleton}
import scala.concurrent.duration.DurationLong
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CustomLoggingFilter @Inject() (
)(implicit val mat: Materializer, ec: ExecutionContext)
    extends Filter
    with Logger {
  override def apply(nextFilter: RequestHeader => Future[Result])(requestHeader: RequestHeader): Future[Result] = {
    val startedAtNanos = System.nanoTime()
    val traceId = TraceId.generate()
    val request = requestHeader.addAttr(TraceId.Attr, traceId)

    logger.info(
      "HTTP リクエストを受信しました {} {} {} {}",
      kv("method", request.method),
      kv("path", request.path),
      kv("queryString", sanitizedQueryString(request)),
      kv("remote_address", request.remoteAddress),
      kv("user_agent", request.headers.get("User-Agent").getOrElse("")),
      kv("traceId", traceId)
    )

    nextFilter(request)
      .map { result =>
        logger.info(
          "HTTP レスポンスを送信しました {} {} {} {} {}",
          kv("method", request.method),
          kv("path", request.path),
          kv("status", result.header.status),
          kv("durationMs", durationMillis(startedAtNanos)),
          kv("remote_address", request.remoteAddress),
          kv("user_agent", request.headers.get("User-Agent").getOrElse("")),
          kv("traceId", traceId)
        )
        result
      }
      .recoverWith { case throwable =>
        logger.error(
          "HTTP レスポンス送信中に例外が発生しました {} {} {} {} {}",
          kv("method", request.method),
          kv("path", request.path),
          kv("durationMs", durationMillis(startedAtNanos)),
          kv("exceptionClass", throwable.getClass.getName),
          kv("remote_address", request.remoteAddress),
          kv("user_agent", request.headers.get("User-Agent").getOrElse("")),
          kv("traceId", traceId),
          throwable
        )
        Future.failed(throwable)
      }
  }

  private def sanitizedQueryString(requestHeader: RequestHeader): String =
    requestHeader.queryString.toSeq
      .sortBy(_._1)
      .flatMap { (key, values) =>
        values.map { value =>
          val sanitizedValue =
            if (CustomLoggingFilter.SensitiveQueryParamNames.contains(key.toLowerCase(Locale.ROOT))) {
              CustomLoggingFilter.RedactedValue
            } else {
              value
            }
          s"${urlEncode(key)}=${urlEncode(sanitizedValue)}"
        }
      }
      .mkString("&")

  private def urlEncode(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8)

  private def durationMillis(startedAtNanos: Long): Long =
    (System.nanoTime() - startedAtNanos).nanos.toMillis
}

object CustomLoggingFilter {
  private val RedactedValue = "(redacted)"
  private val SensitiveQueryParamNames = Set(
    "access_token",
    "client_secret",
    "code",
    "id_token",
    "password",
    "refresh_token",
    "state",
    "token"
  )
}
