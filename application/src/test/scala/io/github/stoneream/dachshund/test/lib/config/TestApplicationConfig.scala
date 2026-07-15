package io.github.stoneream.dachshund.test.lib.config

import com.typesafe.config.{Config, ConfigFactory}
import io.github.stoneream.dachshund.config.{ApplicationConfig, ApplicationConfigReader}

import java.util.Base64

object TestApplicationConfig {
  def apply(
      cookieName: String = "external_auth_state",
      sessionCookieName: String = "session",
      spotifyClientId: String = "spotify-client-id",
      spotifyClientSecret: String = "spotify-client-secret",
      spotifyRedirectUri: String = "http://localhost:9000/spotify/auth/callback",
      encryptionKey: String = Base64.getEncoder.encodeToString(Array.tabulate[Byte](32)(_.toByte))
  ): ApplicationConfig =
    ApplicationConfigReader.load(
      overrides(
        cookieName = cookieName,
        sessionCookieName = sessionCookieName,
        spotifyClientId = spotifyClientId,
        spotifyClientSecret = spotifyClientSecret,
        spotifyRedirectUri = spotifyRedirectUri,
        encryptionKey = encryptionKey
      ).withFallback(ConfigFactory.load()).resolve()
    )

  private def overrides(
      cookieName: String,
      sessionCookieName: String,
      spotifyClientId: String,
      spotifyClientSecret: String,
      spotifyRedirectUri: String,
      encryptionKey: String
  ): Config =
    ConfigFactory.parseString(
      s"""
         |cookie {
         |  session.name = ${quote(sessionCookieName)}
         |  external-auth-state.name = ${quote(cookieName)}
         |}
         |
         |spotify {
         |  client {
         |    client-id = ${quote(spotifyClientId)}
         |    client-secret = ${quote(spotifyClientSecret)}
         |    redirect-uri = ${quote(spotifyRedirectUri)}
         |  }
         |  token.encryption-key = ${quote(encryptionKey)}
         |}
         |""".stripMargin
    )

  private def quote(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
