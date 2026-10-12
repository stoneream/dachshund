package io.github.stoneream.dachshund.controller

import io.github.stoneream.dachshund.action.TraceAction
import io.github.stoneream.dachshund.action.TraceAction.TraceRequest
import io.github.stoneream.dachshund.controller.lib.ControllerBaseImpl
import io.github.stoneream.dachshund.handler.spotify.track.detail.SpotifyTrackDetailHandler
import play.api.mvc.*

import com.google.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class SpotifyTrackDetailController @Inject() (
    cc: ControllerComponents,
    traceAction: TraceAction,
    spotifyTrackDetailHandler: SpotifyTrackDetailHandler
) extends AbstractController(cc)
    with ControllerBaseImpl {
  private given ExecutionContext = cc.executionContext

  def spotifyTrackDetail(trackId: String): Action[AnyContent] = traceAction.async { implicit request: TraceRequest[AnyContent] =>
    val detailRequest = request.copy(request = request.request.addAttr(SpotifyTrackDetailHandler.TrackId, trackId))
    handle(spotifyTrackDetailHandler)(detailRequest)
  }
}
