package io.github.stoneream.dachshund.config

import io.github.stoneream.dachshund.config.auth.AuthConfig
import io.github.stoneream.dachshund.config.cookie.CookieConfig
import io.github.stoneream.dachshund.config.database.DatabaseConfig
import io.github.stoneream.dachshund.config.spotify.SpotifyConfig
import pureconfig.ConfigReader

final case class ApplicationConfig(
    db: DatabaseConfig,
    auth: AuthConfig,
    cookie: CookieConfig,
    spotify: SpotifyConfig
) derives ConfigReader
