package io.github.stoneream.dachshund.handler.spotify.auth.callback

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.action.TraceAction.TraceRequest
import io.github.stoneream.dachshund.config.ApplicationConfig
import io.github.stoneream.dachshund.handler.lib.{HandlerBase, HtmlRendererBase}
import io.github.stoneream.dachshund.lib.datetime.DateTimeService
import io.github.stoneream.dachshund.usecase.spotify.auth.callback.SpotifyAuthCallbackUseCaseInput.{SpotifyAuthorizationCode, SpotifyAuthorizationError, SpotifyAuthorizationState}
import io.github.stoneream.dachshund.usecase.spotify.auth.callback.{SpotifyAuthCallbackUseCase as UseCase, SpotifyAuthCallbackUseCaseException as UseCaseException, SpotifyAuthCallbackUseCaseInput as UseCaseInput, SpotifyAuthCallbackUseCaseOutput as UseCaseOutput}
import play.api.mvc.{AnyContent, Result}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SpotifyAuthCallbackHandler @Inject() (
    override val useCase: UseCase,
    applicationConfig: ApplicationConfig,
    dateTimeService: DateTimeService,
    spotifyAuthCallbackRenderer: SpotifyAuthCallbackRenderer
)(using
    ExecutionContext
) extends HandlerBase[
      TraceRequest[AnyContent],
      UseCaseInput,
      UseCaseOutput,
      UseCaseException,
      Result
    ] {
  private val externalAuthStateCookieName = applicationConfig.cookie.externalAuthState.name

  def handle(request: TraceRequest[AnyContent]): Future[UseCaseInput] =
    for {
      state <- validate("state")(nonBlank(request.getQueryString("state")))(requiredValue("state"))
      externalAuthState <- validate("externalAuthState")(nonBlank(request.cookies.get(externalAuthStateCookieName).map(_.value)))(
        requiredValue("externalAuthState")
      )
    } yield UseCaseInput(
      code = nonBlank(request.getQueryString("code")).map(SpotifyAuthorizationCode(_)),
      state = Some(SpotifyAuthorizationState(state)),
      externalAuthState = Some(SpotifyAuthorizationState(externalAuthState)),
      error = nonBlank(request.getQueryString("error")).map(SpotifyAuthorizationError(_)),
      now = dateTimeService.now()
    )

  override def renderer: HtmlRendererBase[UseCaseOutput, UseCaseException, Result] =
    spotifyAuthCallbackRenderer

  private def nonBlank(value: Option[String]): Option[String] =
    value.map(_.trim).filter(_.nonEmpty)
}
