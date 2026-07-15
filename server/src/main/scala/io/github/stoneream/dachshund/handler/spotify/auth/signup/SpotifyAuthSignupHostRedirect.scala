package io.github.stoneream.dachshund.handler.spotify.auth.signup

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.config.ApplicationConfig
import play.api.mvc.RequestHeader

import java.net.URI
import java.util.Locale
import scala.util.Try

@Singleton
class SpotifyAuthSignupHostRedirect @Inject() (applicationConfig: ApplicationConfig) {
  private val LoginPath = "/spotify/auth/login"
  private val spotifyRedirectUri = Try(URI.create(applicationConfig.spotify.client.redirectUri)).toOption
  private val spotifyRedirectHost = spotifyRedirectUri.flatMap(uri => Option(uri.getHost)).map(normalizeHost)
  private val canonicalLoginUrl = spotifyRedirectUri.flatMap { uri =>
    for {
      scheme <- Option(uri.getScheme).filter(_.nonEmpty)
      authority <- Option(uri.getRawAuthority).filter(_.nonEmpty)
    } yield s"$scheme://$authority$LoginPath"
  }

  def redirectUrlFor(request: RequestHeader): Option[String] =
    for {
      redirectHost <- spotifyRedirectHost
      requestHost <- requestHostName(request)
      url <- canonicalLoginUrl
      if normalizeHost(requestHost) != redirectHost
    } yield url

  private def requestHostName(request: RequestHeader): Option[String] =
    Try(URI.create(s"http://${request.host}").getHost).toOption

  private def normalizeHost(host: String): String =
    host.toLowerCase(Locale.ROOT)
}
