package io.github.stoneream.dachshund.handler.spotify.auth.callback

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.config.ApplicationConfig
import io.github.stoneream.dachshund.config.cookie.CookieSameSite
import io.github.stoneream.dachshund.handler.spotify.SpotifyAuthStateCookie
import io.github.stoneream.dachshund.handler.lib.{HtmlRendererBase, PageMeta}
import io.github.stoneream.dachshund.usecase.spotify.auth.callback.SpotifyAuthCallbackUseCaseOutput.SpotifyAuthCallbackStatus
import io.github.stoneream.dachshund.usecase.spotify.auth.callback.{SpotifyAuthCallbackUseCaseException as UseCaseException, SpotifyAuthCallbackUseCaseOutput as UseCaseOutput}
import play.api.mvc.{Cookie, Result, Results}

@Singleton
class SpotifyAuthCallbackRenderer @Inject() (applicationConfig: ApplicationConfig) extends HtmlRendererBase[UseCaseOutput, UseCaseException, Result] {
  private val externalAuthStateCookieConfig = applicationConfig.cookie.externalAuthState
  private val sessionCookieConfig = applicationConfig.cookie.session

  override def success(output: UseCaseOutput): Result = output.status match {
    case SpotifyAuthCallbackStatus.AuthorizationReceived =>
      output.sessionToken match {
        case Some(sessionToken) =>
          clearAuthState(
            noIndex(
              Results
                .SeeOther("/")
                .withCookies(sessionCookie(sessionToken))
            )
          )
        case None =>
          clearAuthState(
            noIndex(
              Results.InternalServerError("Spotify 認可コールバックを処理できませんでした")
            )
          )
      }
    case SpotifyAuthCallbackStatus.AuthorizationDenied =>
      clearAuthState(
        noIndex(
          Results.BadRequest("Spotify 認可は完了しませんでした")
        )
      )
  }

  override def failure(exception: UseCaseException): Result = exception match {
    case UseCaseException.InvalidCallback(_) =>
      clearAuthState(
        noIndex(
          Results.BadRequest("Spotify 認可コールバックが不正です")
        )
      )
    case UseCaseException.InvalidState =>
      clearAuthState(
        noIndex(
          Results.BadRequest("Spotify 認可 state が不正です")
        )
      )
    case UseCaseException.AuthorizationRequestAlreadyUsed | UseCaseException.AuthorizationRequestExpired =>
      clearAuthState(
        noIndex(
          Results.BadRequest("Spotify 認可 state が不正です")
        )
      )
    case UseCaseException.ProviderError(_) =>
      clearAuthState(
        noIndex(
          Results.BadRequest("Spotify 認可は完了しませんでした")
        )
      )
    case UseCaseException.MissingConfiguration(_) =>
      clearAuthState(
        noIndex(
          Results.InternalServerError("Spotify 認可が設定されていません")
        )
      )
    case _ =>
      clearAuthState(
        noIndex(
          Results.InternalServerError("Spotify 認可コールバックを処理できませんでした")
        )
      )
  }

  private def noIndex(result: Result): Result =
    result.withHeaders(PageMeta.XRobotsTagHeaderName -> PageMeta.NoIndexNoFollow)

  private def clearAuthState(result: Result): Result =
    result.discardingCookies(SpotifyAuthStateCookie.discard(externalAuthStateCookieConfig.name))

  private def sessionCookie(sessionToken: String): Cookie =
    Cookie(
      name = sessionCookieConfig.name,
      value = sessionToken,
      maxAge = sessionCookieConfig.maxAgeSeconds.map(_.toInt),
      path = "/",
      domain = sessionCookieConfig.domain,
      secure = sessionCookieConfig.secure,
      httpOnly = true,
      sameSite = Some(toPlaySameSite(sessionCookieConfig.sameSite))
    )

  private def toPlaySameSite(value: CookieSameSite): Cookie.SameSite =
    value.value match {
      case "strict" => Cookie.SameSite.Strict
      case "none" => Cookie.SameSite.None
      case _ => Cookie.SameSite.Lax
    }
}
