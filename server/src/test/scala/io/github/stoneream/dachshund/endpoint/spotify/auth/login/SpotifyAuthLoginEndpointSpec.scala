package io.github.stoneream.dachshund.endpoint.spotify.auth.login

import io.github.stoneream.dachshund.config.ApplicationConfig
import io.github.stoneream.dachshund.lib.datetime.{BusinessDateTime, DateTimeService}
import io.github.stoneream.dachshund.handler.lib.PageMeta
import io.github.stoneream.dachshund.module.{ApplicationModule, DatabaseInitializer}
import io.github.stoneream.dachshund.model.{ExternalAuthFlowType, ExternalAuthProviderType, ExternalAuthRequestStatus}
import io.github.stoneream.dachshund.service.spotify.auth.access_token.{SpotifyAuthorizationCodeAccessTokenProvider, SpotifyAuthorizationCodeAccessTokenProviderImpl}
import io.github.stoneream.dachshund.service.spotify.oauth_client.{SpotifyOAuthClient, SpotifyOAuthClientImpl}
import io.github.stoneream.dachshund.service.spotify.user_profile_client.{SpotifyUserProfileClient, SpotifyUserProfileClientImpl}
import io.github.stoneream.dachshund.test.lib.PlayApplicationDatabaseSupport
import org.mockito.scalatest.IdiomaticMockito
import org.scalatest.OptionValues
import org.scalatest.featurespec.AnyFeatureSpec
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import scalikejdbc.*

import java.time.LocalDateTime
import scala.concurrent.duration.*

class SpotifyAuthLoginEndpointSpec extends AnyFeatureSpec with PlayApplicationDatabaseSupport with OptionValues with IdiomaticMockito {
  private val fixedNow: BusinessDateTime =
    BusinessDateTime.from("2026-06-21T12:00:00+09:00")
  private val dateTimeService = mock[DateTimeService]
  dateTimeService.now() returns fixedNow

  override protected lazy val app: Application = GuiceApplicationBuilder()
    .disable[ApplicationModule]
    .overrides(
      bind[ApplicationConfig].toInstance(testApplicationConfig),
      bind[DateTimeService].toInstance(dateTimeService),
      bind[SpotifyOAuthClient].to[SpotifyOAuthClientImpl],
      bind[SpotifyUserProfileClient].to[SpotifyUserProfileClientImpl],
      bind[SpotifyAuthorizationCodeAccessTokenProvider].to[SpotifyAuthorizationCodeAccessTokenProviderImpl],
      bind[DatabaseInitializer].toSelf.eagerly()
    )
    .build()

  Feature("Spotify auth login endpoint") {
    Scenario("Spotify 認可リクエストをDBに保存して認可画面へリダイレクトする") {
      val result = route(app, FakeRequest(GET, "/spotify/auth/login").withHeaders(HOST -> "localhost:9000")).value

      assert(status(result) == SEE_OTHER)
      assert(redirectLocation(result).value.startsWith("https://accounts.spotify.com/authorize"))
      val stateCookie = cookies(result).get("external_auth_state").value

      val rows = NamedDB(testApplicationConfig.db.master.connectionPoolName).readOnly { implicit session =>
        sql"""
          select
            flow_type,
            provider_type,
            state,
            redirect_uri,
            scopes,
            status,
            expires_at,
            deleted,
            lock_version
          from external_auth_request
          order by id asc
        """
          .map { rs =>
            ExternalAuthRequestRow(
              flowType = rs.string("flow_type"),
              providerType = rs.string("provider_type"),
              state = rs.string("state"),
              redirectUri = rs.string("redirect_uri"),
              scopes = rs.string("scopes"),
              status = rs.string("status"),
              expiresAt = rs.localDateTime("expires_at"),
              deleted = rs.long("deleted"),
              lockVersion = rs.long("lock_version")
            )
          }
          .list
          .apply()
      }

      assert(
        rows == Seq(
          ExternalAuthRequestRow(
            flowType = ExternalAuthFlowType.Signup.dbValue,
            providerType = ExternalAuthProviderType.Spotify.dbValue,
            state = stateCookie.value,
            redirectUri = "http://localhost:9000/spotify/auth/callback",
            scopes = "user-follow-read",
            status = ExternalAuthRequestStatus.Pending.dbValue,
            expiresAt = fixedNow.plus(600.seconds).toLocalDateTime,
            deleted = 0L,
            lockVersion = 0L
          )
        )
      )
    }

    Scenario("callback host と異なる host で認可開始した場合は callback host の login URL へリダイレクトする") {
      val result = route(app, FakeRequest(GET, "/spotify/auth/login").withHeaders(HOST -> "127.0.0.1:9000")).value

      assert(status(result) == SEE_OTHER)
      assert(redirectLocation(result).value == "http://localhost:9000/spotify/auth/login")
      assert(header(PageMeta.XRobotsTagHeaderName, result).value == PageMeta.NoIndexNoFollow)
      assert(externalAuthRequestCount() == 0)
    }
  }

  private final case class ExternalAuthRequestRow(
      flowType: String,
      providerType: String,
      state: String,
      redirectUri: String,
      scopes: String,
      status: String,
      expiresAt: LocalDateTime,
      deleted: Long,
      lockVersion: Long
  )

  private def externalAuthRequestCount(): Long =
    NamedDB(testApplicationConfig.db.master.connectionPoolName).readOnly { implicit session =>
      sql"select count(*) as count from external_auth_request"
        .map(_.long("count"))
        .single
        .apply()
        .getOrElse(0L)
    }
}
