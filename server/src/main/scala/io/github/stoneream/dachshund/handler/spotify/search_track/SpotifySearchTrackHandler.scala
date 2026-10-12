package io.github.stoneream.dachshund.handler.spotify.search_track

import io.github.stoneream.dachshund.action.TraceAction.TraceRequest
import io.github.stoneream.dachshund.handler.lib.{HandlerAuthPolicy, HandlerBase, HtmlRendererBase}
import io.github.stoneream.dachshund.lib.datetime.DateTimeService
import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor
import io.github.stoneream.dachshund.service.spotify.client.api.spotify_search_track.model.SpotifyTrackSearchPage
import io.github.stoneream.dachshund.usecase.spotify.search_track.{SpotifySearchTrackUseCase as UseCase, SpotifySearchTrackUseCaseException as UseCaseException, SpotifySearchTrackUseCaseInput as UseCaseInput, SpotifySearchTrackUseCaseOutput as UseCaseOutput}
import play.api.mvc.{AnyContent, Result}

import com.google.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
class SpotifySearchTrackHandler @Inject() (
    override val useCase: UseCase,
    spotifySearchTrackRenderer: SpotifySearchTrackRenderer,
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
    validate("offset")(request.getQueryString("offset").getOrElse("0")) { value =>
      val offset = value.toInt
      require(offset >= 0 && offset <= SpotifyTrackSearchPage.MaxOffset)
      offset
    }.map { offset =>
      UseCaseInput(
        user = loggedInUser(request),
        now = dateTimeService.now(),
        query = request.getQueryString("q").getOrElse("").strip(),
        offset = offset
      )
    }(using defaultExecutor)

  override def renderer: HtmlRendererBase[UseCaseOutput, UseCaseException, Result] =
    spotifySearchTrackRenderer
}
