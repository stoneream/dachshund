package io.github.stoneream.dachshund.controller

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.action.TraceAction
import io.github.stoneream.dachshund.action.TraceAction.TraceRequest
import io.github.stoneream.dachshund.controller.lib.ControllerBaseImpl
import io.github.stoneream.dachshund.handler.job_status.artist_releases_sync.ArtistReleasesSyncJobStatusHandler
import io.github.stoneream.dachshund.handler.job_status.followed_artists_sync.FollowedArtistsSyncJobStatusHandler
import io.github.stoneream.dachshund.handler.job_status.lib.JobStatusIndexHandler
import io.github.stoneream.dachshund.handler.job_status.spotify_access_token_refresh.SpotifyAccessTokenRefreshJobStatusHandler
import io.github.stoneream.dachshund.handler.job_status.user_new_release_events_sync.UserNewReleaseEventsSyncJobStatusHandler
import io.github.stoneream.dachshund.handler.job_status.user_new_release_notification_delivery.UserNewReleaseNotificationDeliveryJobStatusHandler
import io.github.stoneream.dachshund.handler.lib.PageMeta
import io.github.stoneream.dachshund.usecase.job_status.context.JobStatusJob
import play.api.mvc.*

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class JobStatusController @Inject() (
    cc: ControllerComponents,
    traceAction: TraceAction,
    indexHandler: JobStatusIndexHandler,
    spotifyAccessTokenRefreshHandler: SpotifyAccessTokenRefreshJobStatusHandler,
    followedArtistsSyncHandler: FollowedArtistsSyncJobStatusHandler,
    artistReleasesSyncHandler: ArtistReleasesSyncJobStatusHandler,
    userNewReleaseEventsSyncHandler: UserNewReleaseEventsSyncJobStatusHandler,
    userNewReleaseNotificationDeliveryHandler: UserNewReleaseNotificationDeliveryJobStatusHandler
) extends AbstractController(cc)
    with ControllerBaseImpl {
  private given ExecutionContext = cc.executionContext

  def index(): Action[AnyContent] = traceAction.async { implicit request: TraceRequest[AnyContent] =>
    handle(indexHandler)(request)
  }

  def detail(jobName: String): Action[AnyContent] = traceAction.async { implicit request: TraceRequest[AnyContent] =>
    JobStatusJob.fromName(jobName) match {
      case Some(JobStatusJob.SpotifyAccessTokenRefresh) =>
        handle(spotifyAccessTokenRefreshHandler)(request)
      case Some(JobStatusJob.FollowedArtistsSync) =>
        handle(followedArtistsSyncHandler)(request)
      case Some(JobStatusJob.ArtistReleasesSync) =>
        handle(artistReleasesSyncHandler)(request)
      case Some(JobStatusJob.UserNewReleaseEventsSync) =>
        handle(userNewReleaseEventsSyncHandler)(request)
      case Some(JobStatusJob.UserNewReleaseNotificationDelivery) =>
        handle(userNewReleaseNotificationDeliveryHandler)(request)
      case None =>
        Future.successful(notFound(jobName))
    }
  }

  private def notFound(jobName: String): Result =
    NotFound(views.html.global_http_error.not_found(s"/job/status/$jobName"))
      .withHeaders(PageMeta.XRobotsTagHeaderName -> PageMeta.NoIndexNoFollow)
}
