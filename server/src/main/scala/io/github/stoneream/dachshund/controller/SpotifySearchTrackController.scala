package io.github.stoneream.dachshund.controller

import io.github.stoneream.dachshund.action.TraceAction
import io.github.stoneream.dachshund.action.TraceAction.TraceRequest
import io.github.stoneream.dachshund.controller.lib.ControllerBaseImpl
import io.github.stoneream.dachshund.handler.spotify.search_track.SpotifySearchTrackHandler
import play.api.mvc.*

import com.google.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class SpotifySearchTrackController @Inject() (
    cc: ControllerComponents,
    traceAction: TraceAction,
    spotifySearchTrackHandler: SpotifySearchTrackHandler
) extends AbstractController(cc)
    with ControllerBaseImpl {
  private given ExecutionContext = cc.executionContext

  def spotifySearchTrack(): Action[AnyContent] = traceAction.async { implicit request: TraceRequest[AnyContent] =>
    handle(spotifySearchTrackHandler)(request)
  }
}
