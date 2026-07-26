package io.github.stoneream.dachshund.module

import io.github.stoneream.dachshund.config.ApplicationConfig
import io.github.stoneream.dachshund.service.spotify.client.{SpotifyClient, SpotifyClientImpl}
import io.github.stoneream.dachshund.service.spotify.oauth_client.{SpotifyOAuthClient, SpotifyOAuthClientImpl}
import io.github.stoneream.dachshund.service.spotify.user_profile_client.{SpotifyUserProfileClient, SpotifyUserProfileClientImpl}
import io.github.stoneream.dachshund.service.spotify.auth.access_token.{SpotifyAuthorizationCodeAccessTokenProvider, SpotifyAuthorizationCodeAccessTokenProviderImpl}
import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}

class ApplicationModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[?]] = Seq(
    bind[ApplicationConfigLoader].toSelf.eagerly(),
    bind[ApplicationConfig].toProvider[ApplicationConfigLoader],
    bind[SpotifyOAuthClient].to[SpotifyOAuthClientImpl],
    bind[SpotifyUserProfileClient].to[SpotifyUserProfileClientImpl],
    bind[SpotifyAuthorizationCodeAccessTokenProvider].to[SpotifyAuthorizationCodeAccessTokenProviderImpl],
    bind[SpotifyClient].to[SpotifyClientImpl],
    bind[DatabaseInitializer].toSelf.eagerly()
  )
}
