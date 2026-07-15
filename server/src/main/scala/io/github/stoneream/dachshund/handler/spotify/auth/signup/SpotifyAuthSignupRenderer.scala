package io.github.stoneream.dachshund.handler.spotify.auth.signup

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.config.ApplicationConfig
import io.github.stoneream.dachshund.handler.spotify.SpotifyAuthStateCookie
import io.github.stoneream.dachshund.handler.lib.{HtmlRendererBase, PageMeta}
import io.github.stoneream.dachshund.usecase.spotify.auth.signup.{SpotifyAuthSignupUseCaseException as UseCaseException, SpotifyAuthSignupUseCaseOutput as UseCaseOutput}
import play.api.mvc.{Result, Results}

@Singleton
class SpotifyAuthSignupRenderer @Inject() (applicationConfig: ApplicationConfig) extends HtmlRendererBase[UseCaseOutput, UseCaseException, Result] {
  private val externalAuthStateCookieConfig = applicationConfig.cookie.externalAuthState

  override def success(output: UseCaseOutput): Result =
    Results
      .Redirect(
        url = output.authorizationEndpoint,
        queryStringParams = Map(
          "response_type" -> Seq(output.responseType),
          "client_id" -> Seq(output.clientId),
          "scope" -> Seq(output.scope),
          "redirect_uri" -> Seq(output.redirectUri),
          "state" -> Seq(output.state)
        )
      )
      .withCookies(
        SpotifyAuthStateCookie.create(
          name = externalAuthStateCookieConfig.name,
          value = output.state,
          maxAgeSeconds = output.stateMaxAgeSeconds
        )
      )
      .withHeaders(PageMeta.XRobotsTagHeaderName -> PageMeta.NoIndexNoFollow)

  override def failure(exception: UseCaseException): Result = exception match {
    case UseCaseException.MissingConfiguration(_) =>
      noIndex(
        Results.InternalServerError("Spotify 認可が設定されていません")
      )
  }

  private def noIndex(result: Result): Result =
    result.withHeaders(PageMeta.XRobotsTagHeaderName -> PageMeta.NoIndexNoFollow)
}
