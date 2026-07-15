package io.github.stoneream.dachshund.handler.spotify.auth.signup

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.action.TraceAction.TraceRequest
import io.github.stoneream.dachshund.handler.lib.{HandlerBase, HtmlRendererBase}
import io.github.stoneream.dachshund.lib.datetime.DateTimeService
import io.github.stoneream.dachshund.usecase.spotify.auth.signup.{SpotifyAuthSignupUseCase as UseCase, SpotifyAuthSignupUseCaseException as UseCaseException, SpotifyAuthSignupUseCaseInput as UseCaseInput, SpotifyAuthSignupUseCaseOutput as UseCaseOutput}
import play.api.mvc.{AnyContent, Result}

import scala.concurrent.Future

@Singleton
class SpotifyAuthSignupHandler @Inject() (
    override val useCase: UseCase,
    dateTimeService: DateTimeService,
    spotifyAuthSignupRenderer: SpotifyAuthSignupRenderer
) extends HandlerBase[
      TraceRequest[AnyContent],
      UseCaseInput,
      UseCaseOutput,
      UseCaseException,
      Result
    ] {

  def handle(_request: TraceRequest[AnyContent]): Future[UseCaseInput] =
    Future.successful(
      UseCaseInput(
        now = dateTimeService.now()
      )
    )

  override def renderer: HtmlRendererBase[UseCaseOutput, UseCaseException, Result] =
    spotifyAuthSignupRenderer
}
