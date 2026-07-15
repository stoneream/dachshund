package io.github.stoneream.dachshund.handler.lib

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.auth.{UserSessionContext, UserSessionContextResolver as ApplicationUserSessionContextResolver}
import io.github.stoneream.dachshund.config.ApplicationConfig
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.logging.TraceLogger
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import play.api.mvc.RequestHeader

import scala.concurrent.Future

@Singleton
class UserContextResolver @Inject() (
    applicationConfig: ApplicationConfig,
    userSessionContextResolver: ApplicationUserSessionContextResolver
) extends TraceLogger {
  private val sessionCookieName = applicationConfig.cookie.session.name

  def resolve(
      request: RequestHeader,
      now: BusinessDateTime
  )(using LoggingContext): Future[UserSessionContext] = {
    val sessionCookie = request.cookies.get(sessionCookieName)
    debug(
      "ユーザーコンテキスト解決を開始しました",
      kv("hasSessionCookie", sessionCookie.isDefined)
    )

    userSessionContextResolver
      .resolve(
        sessionToken = sessionCookie.map(_.value),
        now = now
      )
      .map { context =>
        debug(
          "ユーザーコンテキスト解決を完了しました",
          kv("userSessionContext", contextLabel(context))
        )
        context
      }(using scala.concurrent.ExecutionContext.parasitic)
  }

  private def contextLabel(context: UserSessionContext): String =
    context match {
      case UserSessionContext.NotLoggedIn => "notLoggedIn"
      case _: UserSessionContext.NormalUser => "normalUser"
    }
}
