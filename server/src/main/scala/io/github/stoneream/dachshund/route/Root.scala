package io.github.stoneream.dachshund.route

import io.github.stoneream.dachshund.controller.{HomeController, JobStatusController, SpotifyAuthController, SpotifySearchTrackController, SpotifyTrackDetailController, UserSettingsController}
import play.api.routing.Router.Routes
import play.api.routing.SimpleRouter
import play.api.routing.sird.*

import com.google.inject.Inject

class Root @Inject() (
    homeController: HomeController,
    spotifyAuthController: SpotifyAuthController,
    userSettingsController: UserSettingsController,
    jobStatusController: JobStatusController,
    spotifySearchTrackController: SpotifySearchTrackController,
    spotifyTrackDetailController: SpotifyTrackDetailController
) extends SimpleRouter {
  override def routes: Routes = {
    case GET(p"/") =>
      homeController.index()
    case GET(p"/job/status") =>
      jobStatusController.index()
    case GET(p"/job/status/$jobName") =>
      jobStatusController.detail(jobName)
    case GET(p"/user-settings") =>
      userSettingsController.index()
    case POST(p"/user-settings") =>
      userSettingsController.save()
    case GET(p"/spotify/auth/login") =>
      spotifyAuthController.login()
    case GET(p"/spotify/search-track") =>
      spotifySearchTrackController.spotifySearchTrack()
    case GET(p"/spotify/track/detail/$trackId") =>
      spotifyTrackDetailController.spotifyTrackDetail(trackId)
    case GET(p"/spotify/auth/callback") =>
      spotifyAuthController.callback()
  }
}
