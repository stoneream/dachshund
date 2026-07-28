package io.github.stoneream.dachshund.service.spotify.client.lib

import io.github.stoneream.dachshund.config.spotify.SpotifyRequestPolicyConfig
import io.github.stoneream.dachshund.test.lib.config.TestApplicationConfig
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.featurespec.AnyFeatureSpec

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*

class SpotifyRequestThrottlerSpec extends AnyFeatureSpec with ScalaFutures {
  private given ExecutionContext = ExecutionContext.global

  Feature("Spotify request throttler") {
    Scenario("設定された間隔でリクエスト開始を直列化する") {
      val throttler = new SpotifyRequestThrottler(applicationConfig(pacingInterval = 200.millis))

      assert(throttler.acquirePermit().futureValue == Right(()))
      val secondPermit = throttler.acquirePermit()
      assert(!secondPermit.isCompleted)
      assert(secondPermit.futureValue == Right(()))
    }

    Scenario("短い停止期間を再設定しても待機期限を短縮しない") {
      val throttler = new SpotifyRequestThrottler(applicationConfig(pacingInterval = Duration.Zero))

      throttler.registerRateLimit(10.seconds)
      throttler.registerRateLimit(1.second)

      val throttled = throttler.acquirePermit().futureValue.left.toOption.get
      assert(throttled.retryAfter > 9.seconds)
    }

    Scenario("同じ理由の block を再設定すると待機期限を延長する") {
      val throttler = new SpotifyRequestThrottler(applicationConfig(pacingInterval = Duration.Zero))

      throttler.registerRateLimit(500.millis)
      throttler.registerRateLimit(2.seconds)

      val throttled = throttler.acquirePermit().futureValue.left.toOption.get
      assert(throttled.retryAfter > 1500.millis)
    }

    Scenario("block の期限後はリクエストを再開する") {
      val throttler = new SpotifyRequestThrottler(applicationConfig(pacingInterval = Duration.Zero))

      throttler.registerRateLimit(20.millis)
      Thread.sleep(50L)

      assert(throttler.acquirePermit().futureValue == Right(()))
    }
  }

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = 2.seconds, interval = 10.millis)

  private def applicationConfig(
      pacingInterval: FiniteDuration
  ) = {
    val base = TestApplicationConfig()
    base.copy(
      spotify = base.spotify.copy(
        client = base.spotify.client.copy(
          requestPolicy = SpotifyRequestPolicyConfig(
            pacingInterval = pacingInterval,
            rateLimitFallbackDelay = 30.seconds
          )
        )
      )
    )
  }
}
