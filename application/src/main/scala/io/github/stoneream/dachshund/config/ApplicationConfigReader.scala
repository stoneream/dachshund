package io.github.stoneream.dachshund.config

import com.typesafe.config.Config
import io.github.stoneream.dachshund.config.cookie.CookieConfig
import io.github.stoneream.dachshund.config.database.DatabasePoolConfig
import io.github.stoneream.dachshund.config.spotify.SpotifyConfig
import io.github.stoneream.dachshund.logging.Logger
import pureconfig.ConfigSource

object ApplicationConfigReader extends Logger {
  def load(config: Config): ApplicationConfig = {
    val applicationConfig = ConfigSource.fromConfig(config).loadOrThrow[ApplicationConfig]
    logging(applicationConfig)
    applicationConfig
  }

  private def logging(applicationConfig: ApplicationConfig): Unit = {
    logger.info("アプリケーション設定を読み込みました")
    logDatabaseConfig(applicationConfig)
    logAuthConfig(applicationConfig)
    logCookieConfig(applicationConfig.cookie)
    logSpotifyConfig(applicationConfig.spotify)
  }

  private def logDatabaseConfig(applicationConfig: ApplicationConfig): Unit = {
    logger.info(
      "DB 設定を読み込みました",
      kv("db.poolCount", applicationConfig.db.allPools.size)
    )

    logPoolConfig("master", applicationConfig.db.master)
    applicationConfig.db.slave.foreach(logPoolConfig("slave", _))
  }

  private def logPoolConfig(role: String, poolConfig: DatabasePoolConfig): Unit = {
    val hikariConfig = poolConfig.hikari

    logger.info(
      "DB コネクションプール設定を読み込みました",
      kv("db.role", role),
      kv("db.name", poolConfig.name),
      kv("db.driver", optionalValue(poolConfig.configuredDriver)),
      kv("db.url", maskedValue(poolConfig.url)),
      kv("db.user", maskedValue(poolConfig.user)),
      kv("db.password", maskedValue(poolConfig.password)),
      kv("db.hikari.poolName", hikariConfig.poolName),
      kv("db.hikari.maximumPoolSize", hikariConfig.maximumPoolSize),
      kv("db.hikari.minimumIdle", hikariConfig.minimumIdle),
      kv("db.hikari.connectionTimeout", hikariConfig.connectionTimeout),
      kv("db.hikari.idleTimeout", hikariConfig.idleTimeout),
      kv("db.hikari.maxLifetime", hikariConfig.maxLifetime),
      kv("db.hikari.validationTimeout", hikariConfig.validationTimeout)
    )
  }

  private def logCookieConfig(cookieConfig: CookieConfig): Unit = {
    logger.info(
      "cookie 設定を読み込みました",
      kv("cookie.session.name", cookieConfig.session.name),
      kv("cookie.session.secure", cookieConfig.session.secure),
      kv("cookie.session.sameSite", cookieConfig.session.sameSite.value),
      kv("cookie.session.domain", optionalValue(cookieConfig.session.domain)),
      kv("cookie.session.maxAgeSeconds", cookieConfig.session.maxAgeSeconds),
      kv("cookie.externalAuthState.name", cookieConfig.externalAuthState.name)
    )
  }

  private def logAuthConfig(applicationConfig: ApplicationConfig): Unit = {
    val signingConfig = applicationConfig.auth.session.signing
    logger.info(
      "認証設定を読み込みました",
      kv("auth.session.signing.currentKid", signingConfig.currentKid),
      kv("auth.session.signing.keyCount", signingConfig.keys.size)
    )
  }

  private def logSpotifyConfig(spotifyConfig: SpotifyConfig): Unit = {
    val client = spotifyConfig.client
    val token = spotifyConfig.token

    logger.info(
      "Spotify 設定を読み込みました",
      kv("spotify.client.apiBaseUrl", client.apiBaseUrl),
      kv("spotify.client.accountsBaseUrl", client.accountsBaseUrl),
      kv("spotify.client.clientId.configured", configured(client.clientId)),
      kv("spotify.client.clientSecret.configured", configured(client.clientSecret)),
      kv("spotify.client.redirectUri.configured", configured(client.redirectUri)),
      kv("spotify.client.connectTimeout", client.connectTimeout.toString),
      kv("spotify.client.requestTimeout", client.requestTimeout.toString),
      kv("spotify.client.retry.maxAttempts", client.retry.maxAttempts),
      kv("spotify.token.refreshMargin", token.refreshMargin.toString),
      kv("spotify.token.encryptionKey.configured", configured(token.encryptionKey)),
      kv("spotify.token.encryptionKeyVersion", token.encryptionKeyVersion)
    )
  }

  private def configured(value: String): Boolean =
    value.trim.nonEmpty
}
