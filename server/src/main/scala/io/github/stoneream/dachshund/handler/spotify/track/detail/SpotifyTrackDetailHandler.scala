package io.github.stoneream.dachshund.handler.spotify.track.detail

import io.github.stoneream.dachshund.action.TraceAction.TraceRequest
import io.github.stoneream.dachshund.handler.lib.{HandlerAuthPolicy, HandlerBase, HtmlRendererBase}
import io.github.stoneream.dachshund.lib.datetime.DateTimeService
import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor
import io.github.stoneream.dachshund.usecase.spotify.track.detail.{SpotifyTrackDetailUseCase as UseCase, SpotifyTrackDetailUseCaseException as UseCaseException, SpotifyTrackDetailUseCaseInput as UseCaseInput, SpotifyTrackDetailUseCaseOutput as UseCaseOutput}
import play.api.mvc.{AnyContent, Result}
import play.api.libs.typedmap.TypedKey

import com.google.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
class SpotifyTrackDetailHandler @Inject() (
    override val useCase: UseCase,
    spotifyTrackDetailRenderer: SpotifyTrackDetailRenderer,
    dateTimeService: DateTimeService,
    defaultExecutor: DefaultExecutor
) extends HandlerBase[
      TraceRequest[AnyContent],
      UseCaseInput,
      UseCaseOutput,
      UseCaseException,
      Result
    ] {

  override def authPolicy: HandlerAuthPolicy = HandlerAuthPolicy.LoginRequired

  override def handle(request: TraceRequest[AnyContent]): Future[UseCaseInput] =
    validate("track_id")(request.attrs.get(SpotifyTrackDetailHandler.TrackId)) { value =>
      val id = requiredValue("track_id")(value)
      require(id.matches("[A-Za-z0-9]{22}"))
      id
    }.map { trackId =>
      UseCaseInput(loggedInUser(request), dateTimeService.now(), trackId)
    }(using defaultExecutor)

  override def renderer: HtmlRendererBase[UseCaseOutput, UseCaseException, Result] =
    spotifyTrackDetailRenderer
}

object SpotifyTrackDetailHandler {
  val TrackId: TypedKey[String] = TypedKey("spotifyTrackId")
}
