package io.github.stoneream.dachshund.route

import io.github.stoneream.dachshund.controller.{HomeController, SpotifyAuthController}
import play.api.routing.Router.Routes
import play.api.routing.SimpleRouter
import play.api.routing.sird.*

import com.google.inject.Inject

class Root @Inject() (
    homeController: HomeController,
    spotifyAuthController: SpotifyAuthController
) extends SimpleRouter {
  override def routes: Routes = {
    case GET(p"/") =>
      homeController.index()
    case GET(p"/spotify/auth/login") =>
      spotifyAuthController.login()
    case GET(p"/spotify/auth/callback") =>
      spotifyAuthController.callback()
  }
}
