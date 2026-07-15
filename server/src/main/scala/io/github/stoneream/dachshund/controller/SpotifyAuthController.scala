package io.github.stoneream.dachshund.controller

import io.github.stoneream.dachshund.action.TraceAction
import io.github.stoneream.dachshund.action.TraceAction.TraceRequest
import io.github.stoneream.dachshund.controller.lib.ControllerBaseImpl
import io.github.stoneream.dachshund.handler.lib.PageMeta
import io.github.stoneream.dachshund.handler.spotify.auth.callback.SpotifyAuthCallbackHandler
import io.github.stoneream.dachshund.handler.spotify.auth.signup.{SpotifyAuthSignupHandler, SpotifyAuthSignupHostRedirect}
import play.api.mvc.*

import com.google.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SpotifyAuthController @Inject() (
    cc: ControllerComponents,
    traceAction: TraceAction,
    spotifyAuthSignupHandler: SpotifyAuthSignupHandler,
    spotifyAuthCallbackHandler: SpotifyAuthCallbackHandler,
    spotifyAuthSignupHostRedirect: SpotifyAuthSignupHostRedirect
) extends AbstractController(cc)
    with ControllerBaseImpl {
  private given ExecutionContext = cc.executionContext

  def login(): Action[AnyContent] = traceAction.async { implicit request: TraceRequest[AnyContent] =>
    spotifyAuthSignupHostRedirect.redirectUrlFor(request) match {
      case Some(url) =>
        Future.successful(
          Redirect(url)
            .withHeaders(PageMeta.XRobotsTagHeaderName -> PageMeta.NoIndexNoFollow)
        )
      case None =>
        handle(spotifyAuthSignupHandler)(request)
    }
  }

  def callback(): Action[AnyContent] = traceAction.async { implicit request: TraceRequest[AnyContent] =>
    handle(spotifyAuthCallbackHandler)(request)
  }
}
