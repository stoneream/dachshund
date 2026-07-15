package io.github.stoneream.dachshund.daemon.config

import org.scalatest.featurespec.AnyFeatureSpec

class JobNameSpec extends AnyFeatureSpec {
  Feature("job name") {
    Scenario("小文字英数字、ハイフン、アンダースコア、ドットだけの name は受け入れる") {
      val result = JobName.validate("spotify-access_token.refresh", "daemon.jobs.test.name")

      assert(result == Right(JobName("spotify-access_token.refresh")))
    }

    Scenario("大文字を含む name は拒否する") {
      val result = JobName.validate("SpotifyAccessTokenRefresh", "daemon.jobs.test.name")

      assert(result.isLeft)
    }
  }
}
