package io.github.stoneream.dachshund.endpoint.home

import io.github.stoneream.dachshund.config.ApplicationConfig
import io.github.stoneream.dachshund.endpoint.home.HomeEndpointFixture.*
import io.github.stoneream.dachshund.infra.db.transaction.DatabaseRole
import io.github.stoneream.dachshund.infra.db.writer.{ArtistReleasesWriter, SpotifyUserWriter, UserFollowedArtistsWriter, UserNewReleaseEventsWriter, UserSessionTokenWriter}
import io.github.stoneream.dachshund.lib.auth.SessionTokenService
import io.github.stoneream.dachshund.lib.datetime.DateTimeService
import io.github.stoneream.dachshund.module.{ApplicationModule, DatabaseInitializer}
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
import play.api.mvc.Cookie
import play.api.test.FakeRequest
import play.api.test.Helpers.*

class HomeEndpointSpec extends AnyFeatureSpec with PlayApplicationDatabaseSupport with OptionValues with IdiomaticMockito {
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

  private val userWriter = new SpotifyUserWriter
  private val followedArtistWriter = new UserFollowedArtistsWriter
  private val artistReleasesWriter = new ArtistReleasesWriter
  private val userNewReleaseEventsWriter = new UserNewReleaseEventsWriter
  private val userSessionTokenWriter = new UserSessionTokenWriter

  Feature("Home endpoint") {
    Scenario("ログインユーザーの新着リリースを月ごと、日ごとの降順に表示する") {
      val sessionToken = databaseTransaction.localTx(DatabaseRole.Master) { implicit session =>
        val userId = userWriter.write(UserRow)
        followedArtistWriter.write(followedArtistRow(userId))

        val juneReleaseId = artistReleasesWriter.write(JuneReleaseRow)
        val julyFirstReleaseId = artistReleasesWriter.write(JulyFirstReleaseRow)
        val julySecondReleaseId = artistReleasesWriter.write(JulySecondReleaseRow)
        userNewReleaseEventsWriter.write(juneEventRow(userId, juneReleaseId))
        userNewReleaseEventsWriter.write(julyFirstEventRow(userId, julyFirstReleaseId))
        userNewReleaseEventsWriter.write(julySecondEventRow(userId, julySecondReleaseId))

        val sessionTokenService = new SessionTokenService(testApplicationConfig, dateTimeService)
        val issuedSessionToken = sessionTokenService.issue(userId)
        userSessionTokenWriter.write(userSessionTokenRow(userId, issuedSessionToken))
        issuedSessionToken.value
      }

      val result = route(
        app,
        FakeRequest(GET, "/")
          .withHeaders(HOST -> "localhost:9000")
          .withCookies(Cookie(testApplicationConfig.cookie.session.name, sessionToken))
      ).value
      val html = contentAsString(result)

      assert(status(result) == OK)
      assert(html.contains("""<header class="site-header">"""))
      assert(html.contains("DACHSHUND"))
      assert(html.contains("display user"))
      assert(html.contains("""<table class="release-table">"""))
      assert(html.contains("""<tbody class="release-month-group">"""))
      assert(html.contains("2026年7月"))
      assert(html.contains("2026年6月"))
      assert(html.indexOf("2026年7月") < html.indexOf("2026年6月"))
      assert(html.contains("2026-07-06"))
      assert(html.contains("2026-06-30"))
      assert(html.contains("""<span class="release-title">July Second Release</span>"""))
      assert(html.contains("""<span class="release-title">July First Release</span>"""))
      assert(html.indexOf("July Second Release") < html.indexOf("July First Release"))
      assert(html.contains("""<span class="release-title">June Release</span>"""))
      assert(!html.contains("""<a class="release-title""""))
      assert(html.contains("Artist Name"))
      assert(html.contains("Label Name"))
      assert(html.contains("""<th class="release-link-heading" scope="col">link</th>"""))
      assert(
        html.contains(
          """<a class="release-link" href="https://open.spotify.com/album/spotify-release-july-2" target="_blank" rel="noopener noreferrer">URL</a>"""
        )
      )
      assert(!html.contains("track-list"))
    }
  }
}
