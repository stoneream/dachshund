package io.github.stoneream.dachshund.daemon

import io.github.stoneream.dachshund.service.spotify.oauth_client.SpotifyOAuthClient
import io.github.stoneream.dachshund.service.spotify.oauth_client.SpotifyOAuthClient.TokenResponse
import io.github.stoneream.dachshund.usecase.daemon.ready.DaemonReadyUseCaseException
import org.mockito.scalatest.IdiomaticMockito
import org.scalatest.featurespec.AnyFeatureSpec

import scala.concurrent.Future

class DaemonMainSpec extends AnyFeatureSpec with IdiomaticMockito with DaemonMainSpecSupport {
  Feature("daemon main") {
    Scenario("readiness check が成功した場合は scheduler へ進む") {
      val applicationConfig = uniquePoolApplicationConfig()
      val spotifyOAuthClient = mock[SpotifyOAuthClient]
      spotifyOAuthClient.requestClientCredentialsAccessToken("spotify-client-id", "spotify-client-secret")(using *) returns
        Future.successful(TokenResponse("daemon-access-token", "Bearer", 3600, None, None))
      val scheduler = new ReachedScheduler
      val main = daemonMain(applicationConfig, spotifyOAuthClient, scheduler)

      try {
        val failure = runMainFailure(main)

        assert(failure == SchedulerReached)
        assert(scheduler.runCount == 1)
      } finally {
        unsafeRun(main.close())
      }
    }

    Scenario("readiness check が失敗した場合は scheduler へ進まない") {
      val applicationConfig = uniquePoolApplicationConfig()
      val readyFailure = new RuntimeException("client credentials failed")
      val spotifyOAuthClient = mock[SpotifyOAuthClient]
      spotifyOAuthClient.requestClientCredentialsAccessToken("spotify-client-id", "spotify-client-secret")(using *) returns Future.failed(readyFailure)
      val scheduler = new ReachedScheduler
      val main = daemonMain(applicationConfig, spotifyOAuthClient, scheduler)

      try {
        val failure = runMainFailure(main)

        failure match {
          case unavailable: DaemonReadyUseCaseException.Unavailable =>
            assert(unavailable.causeException == readyFailure)
            assert(unavailable.getCause == readyFailure)
          case _ =>
            fail(s"unexpected failure: ${failure.getClass.getName}")
        }
        assert(scheduler.runCount == 0)
      } finally {
        unsafeRun(main.close())
      }
    }
  }
}
