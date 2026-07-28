package io.github.stoneream.dachshund.daemon.config

import org.scalatest.featurespec.AnyFeatureSpec
import pureconfig.error.ConfigReaderException

import com.typesafe.config.ConfigFactory
import scala.concurrent.duration.*

class DaemonConfigReaderSpec extends AnyFeatureSpec {
  Feature("daemon config") {
    Scenario("Spotify access token refresh job の設定値が正数の場合は読み込める") {
      val config = daemonConfig(interval = "60s", timeout = "5m", batchSize = 50)

      val result = DaemonConfigReader.load(config)
      val executorsConfig = result.executors
      val jobConfig = result.jobs.spotifyAccessTokenRefresh
      val followedArtistsSyncQueueJobConfig = result.jobs.followedArtistsSyncQueue
      val followedArtistsSyncJobConfig = result.jobs.followedArtistsSync
      val artistReleaseSyncQueueJobConfig = result.jobs.artistReleaseSyncQueue
      val artistReleasesSyncJobConfig = result.jobs.artistReleasesSync
      val userNewReleaseEventsSyncJobConfig = result.jobs.userNewReleaseEventsSync
      val userNewReleaseNotificationDeliveryQueueJobConfig = result.jobs.userNewReleaseNotificationDeliveryQueue
      val commonJobConfig: JobConfig = jobConfig
      val followedCommonJobConfig: JobConfig = followedArtistsSyncQueueJobConfig
      val syncCommonJobConfig: JobConfig = followedArtistsSyncJobConfig
      val artistReleaseCommonJobConfig: JobConfig = artistReleaseSyncQueueJobConfig
      val artistReleasesCommonJobConfig: JobConfig = artistReleasesSyncJobConfig
      val userNewReleaseEventsCommonJobConfig: JobConfig = userNewReleaseEventsSyncJobConfig
      val userNewReleaseNotificationDeliveryQueueCommonJobConfig: JobConfig = userNewReleaseNotificationDeliveryQueueJobConfig

      assert(executorsConfig.defaultExecutor == DaemonExecutorConfig(2, 1.second))
      assert(executorsConfig.databaseExecutor == DaemonExecutorConfig(16, 1.second))
      assert(executorsConfig.ioDispatcher == DaemonExecutorConfig(16, 1.second))
      assert(commonJobConfig.setting.name == JobName("spotify-access-token-refresh"))
      assert(commonJobConfig.setting.enabled)
      assert(commonJobConfig.setting.schedule == JobSchedule.Every(60.seconds))
      assert(commonJobConfig.setting.timeout == 5.minutes)
      assert(commonJobConfig.setting.retryPolicy == JobRetryPolicy(3, 1.second, 30.seconds, Some(0.2)))
      assert(jobConfig.batchSize == 50)
      assert(followedCommonJobConfig.setting.name == JobName("followed-artists-sync-queue"))
      assert(followedCommonJobConfig.setting.enabled)
      assert(followedCommonJobConfig.setting.schedule == JobSchedule.Every(1.hour))
      assert(followedCommonJobConfig.setting.timeout == 5.minutes)
      assert(followedCommonJobConfig.setting.retryPolicy == JobRetryPolicy(3, 1.second, 30.seconds, Some(0.2)))
      assert(syncCommonJobConfig.setting.name == JobName("followed-artists-sync"))
      assert(syncCommonJobConfig.setting.enabled)
      assert(syncCommonJobConfig.setting.schedule == JobSchedule.Every(1.minute))
      assert(syncCommonJobConfig.setting.timeout == 10.minutes)
      assert(syncCommonJobConfig.setting.retryPolicy == JobRetryPolicy(3, 1.second, 30.seconds, Some(0.2)))
      assert(followedArtistsSyncJobConfig.batchSize == 25)
      assert(followedArtistsSyncJobConfig.processingLease == 1.hour)
      assert(artistReleaseCommonJobConfig.setting.name == JobName("artist-release-sync-queue"))
      assert(artistReleaseCommonJobConfig.setting.enabled)
      assert(artistReleaseCommonJobConfig.setting.schedule == JobSchedule.Every(1.hour))
      assert(artistReleaseCommonJobConfig.setting.timeout == 5.minutes)
      assert(artistReleaseCommonJobConfig.setting.retryPolicy == JobRetryPolicy(3, 1.second, 30.seconds, Some(0.2)))
      assert(artistReleasesCommonJobConfig.setting.name == JobName("artist-releases-sync"))
      assert(artistReleasesCommonJobConfig.setting.enabled)
      assert(artistReleasesCommonJobConfig.setting.schedule == JobSchedule.Every(1.minute))
      assert(artistReleasesCommonJobConfig.setting.timeout == 10.minutes)
      assert(artistReleasesCommonJobConfig.setting.retryPolicy == JobRetryPolicy(3, 1.second, 30.seconds, Some(0.2)))
      assert(artistReleasesSyncJobConfig.batchSize == 5)
      assert(artistReleasesSyncJobConfig.processingLease == 1.hour)
      assert(userNewReleaseEventsCommonJobConfig.setting.name == JobName("user-new-release-events-sync"))
      assert(userNewReleaseEventsCommonJobConfig.setting.enabled)
      assert(userNewReleaseEventsCommonJobConfig.setting.schedule == JobSchedule.Every(1.minute))
      assert(userNewReleaseEventsCommonJobConfig.setting.timeout == 5.minutes)
      assert(userNewReleaseEventsCommonJobConfig.setting.retryPolicy == JobRetryPolicy(3, 1.second, 30.seconds, Some(0.2)))
      assert(userNewReleaseEventsSyncJobConfig.batchSize == 500)
      assert(userNewReleaseNotificationDeliveryQueueCommonJobConfig.setting.name == JobName("user-new-release-notification-delivery-queue"))
      assert(userNewReleaseNotificationDeliveryQueueCommonJobConfig.setting.enabled)
      assert(userNewReleaseNotificationDeliveryQueueCommonJobConfig.setting.schedule == JobSchedule.Every(1.minute))
      assert(userNewReleaseNotificationDeliveryQueueCommonJobConfig.setting.timeout == 10.minutes)
      assert(userNewReleaseNotificationDeliveryQueueCommonJobConfig.setting.retryPolicy == JobRetryPolicy(3, 1.second, 30.seconds, Some(0.2)))
      assert(userNewReleaseNotificationDeliveryQueueJobConfig.batchSize == 25)
      assert(userNewReleaseNotificationDeliveryQueueJobConfig.processingLease == 1.hour)
    }

    Scenario("Spotify access token refresh job の enabled が false の場合は false として読み込む") {
      val config = daemonConfig(
        interval = "60s",
        timeout = "5m",
        batchSize = 50,
        spotifyAccessTokenRefreshEnabledConfig = Some("enabled = false")
      )

      val result = DaemonConfigReader.load(config)

      assert(!result.jobs.spotifyAccessTokenRefresh.setting.enabled)
    }

    Scenario("Spotify access token refresh job の enabled は後続設定で上書きできる") {
      val config = daemonConfig(
        interval = "60s",
        timeout = "5m",
        batchSize = 50,
        spotifyAccessTokenRefreshEnabledConfig = Some("enabled = true\n    enabled = false")
      )

      val result = DaemonConfigReader.load(config)

      assert(!result.jobs.spotifyAccessTokenRefresh.setting.enabled)
    }

    Scenario("executor の thread count が 0 の場合は読み込みを拒否する") {
      val config = daemonConfig(interval = "60s", timeout = "5m", batchSize = 50, defaultExecutorThreadCount = 0)

      assertThrows[ConfigReaderException[?]] {
        DaemonConfigReader.load(config)
      }
    }

    Scenario("executor の shutdown grace period が負数の場合は読み込みを拒否する") {
      val config =
        daemonConfig(interval = "60s", timeout = "5m", batchSize = 50, defaultExecutorShutdownGracePeriod = "-1s")

      assertThrows[ConfigReaderException[?]] {
        DaemonConfigReader.load(config)
      }
    }

    Scenario("Spotify access token refresh job の batch size が 0 の場合は読み込みを拒否する") {
      val config = daemonConfig(interval = "60s", timeout = "5m", batchSize = 0)

      assertThrows[ConfigReaderException[?]] {
        DaemonConfigReader.load(config)
      }
    }

    Scenario("Followed artists sync job の batch size が 0 の場合は読み込みを拒否する") {
      val config = daemonConfig(interval = "60s", timeout = "5m", batchSize = 50, followedSyncBatchSize = 0)

      assertThrows[ConfigReaderException[?]] {
        DaemonConfigReader.load(config)
      }
    }

    Scenario("Followed artists sync job の processing lease が 0 の場合は読み込みを拒否する") {
      val config = daemonConfig(interval = "60s", timeout = "5m", batchSize = 50, followedSyncProcessingLease = "0s")

      assertThrows[ConfigReaderException[?]] {
        DaemonConfigReader.load(config)
      }
    }

    Scenario("Artist releases sync job の batch size が 0 の場合は読み込みを拒否する") {
      val config = daemonConfig(interval = "60s", timeout = "5m", batchSize = 50, artistReleasesSyncBatchSize = 0)

      assertThrows[ConfigReaderException[?]] {
        DaemonConfigReader.load(config)
      }
    }

    Scenario("Artist releases sync job の processing lease が 0 の場合は読み込みを拒否する") {
      val config = daemonConfig(interval = "60s", timeout = "5m", batchSize = 50, artistReleasesSyncProcessingLease = "0s")

      assertThrows[ConfigReaderException[?]] {
        DaemonConfigReader.load(config)
      }
    }

    Scenario("User new release events sync job の batch size が 0 の場合は読み込みを拒否する") {
      val config = daemonConfig(interval = "60s", timeout = "5m", batchSize = 50, userNewReleaseEventsSyncBatchSize = 0)

      assertThrows[ConfigReaderException[?]] {
        DaemonConfigReader.load(config)
      }
    }

    Scenario("User new release notification delivery queue job の batch size が 0 の場合は読み込みを拒否する") {
      val config = daemonConfig(interval = "60s", timeout = "5m", batchSize = 50, userNewReleaseNotificationDeliveryQueueBatchSize = 0)

      assertThrows[ConfigReaderException[?]] {
        DaemonConfigReader.load(config)
      }
    }

    Scenario("User new release notification delivery queue job の processing lease が 0 の場合は読み込みを拒否する") {
      val config = daemonConfig(interval = "60s", timeout = "5m", batchSize = 50, userNewReleaseNotificationDeliveryQueueProcessingLease = "0s")

      assertThrows[ConfigReaderException[?]] {
        DaemonConfigReader.load(config)
      }
    }

    Scenario("Spotify access token refresh job の interval が 0 の場合は読み込みを拒否する") {
      val config = daemonConfig(interval = "0s", timeout = "5m", batchSize = 50)

      assertThrows[ConfigReaderException[?]] {
        DaemonConfigReader.load(config)
      }
    }

    Scenario("Spotify access token refresh job の timeout が 0 の場合は読み込みを拒否する") {
      val config = daemonConfig(interval = "60s", timeout = "0s", batchSize = 50)

      assertThrows[ConfigReaderException[?]] {
        DaemonConfigReader.load(config)
      }
    }

    Scenario("Spotify access token refresh job の retry max attempts が 0 の場合は読み込みを拒否する") {
      val config = daemonConfig(interval = "60s", timeout = "5m", retryMaxAttempts = 0, batchSize = 50)

      assertThrows[ConfigReaderException[?]] {
        DaemonConfigReader.load(config)
      }
    }

    Scenario("Spotify access token refresh job の retry delay が負数の場合は読み込みを拒否する") {
      val config = daemonConfig(interval = "60s", timeout = "5m", retryBaseDelay = "-1s", batchSize = 50)

      assertThrows[ConfigReaderException[?]] {
        DaemonConfigReader.load(config)
      }
    }

    Scenario("Spotify access token refresh job の retry jitter ratio が負数の場合は読み込みを拒否する") {
      val config = daemonConfig(interval = "60s", timeout = "5m", retryJitterRatio = "-0.1", batchSize = 50)

      assertThrows[ConfigReaderException[?]] {
        DaemonConfigReader.load(config)
      }
    }

    Scenario("Spotify access token refresh job の retry jitter ratio が null の場合は未設定として読み込む") {
      val config = daemonConfig(interval = "60s", timeout = "5m", retryJitterRatio = "null", batchSize = 50)

      val result = DaemonConfigReader.load(config)

      assert(result.jobs.spotifyAccessTokenRefresh.setting.retryPolicy.jitterRatio.isEmpty)
    }

    Scenario("Spotify access token refresh job の必須設定が null または未設定の場合は読み込みを拒否する") {
      val nullConfigs = Seq(
        daemonConfig(interval = "60s", timeout = "5m", batchSize = 50, spotifyAccessTokenRefreshEnabledConfig = Some("enabled = null")),
        daemonConfig(interval = "60s", timeout = "5m", batchSize = 50, spotifyAccessTokenRefreshEnabledConfig = None),
        daemonConfig(interval = "null", timeout = "5m", batchSize = 50),
        daemonConfig(interval = "60s", timeout = "null", batchSize = 50),
        daemonConfig(interval = "60s", timeout = "5m", retryConfig = Some("retry = null"), batchSize = 50),
        daemonConfig(interval = "60s", timeout = "5m", retryBaseDelay = "null", batchSize = 50)
      )

      nullConfigs.foreach { config =>
        assertThrows[ConfigReaderException[?]] {
          DaemonConfigReader.load(config)
        }
      }
    }
  }

  private def daemonConfig(
      interval: String,
      timeout: String,
      batchSize: Int,
      retryMaxAttempts: Int = 3,
      retryBaseDelay: String = "1s",
      retryMaxDelay: String = "30s",
      retryJitterRatio: String = "0.2",
      defaultExecutorThreadCount: Int = 2,
      defaultExecutorShutdownGracePeriod: String = "1s",
      databaseExecutorThreadCount: Int = 16,
      databaseExecutorShutdownGracePeriod: String = "1s",
      ioDispatcherThreadCount: Int = 16,
      ioDispatcherShutdownGracePeriod: String = "1s",
      followedSyncBatchSize: Int = 25,
      followedSyncProcessingLease: String = "1h",
      artistReleasesSyncBatchSize: Int = 5,
      artistReleasesSyncProcessingLease: String = "1h",
      userNewReleaseEventsSyncBatchSize: Int = 500,
      userNewReleaseNotificationDeliveryQueueBatchSize: Int = 25,
      userNewReleaseNotificationDeliveryQueueProcessingLease: String = "1h",
      spotifyAccessTokenRefreshEnabledConfig: Option[String] = Some("enabled = true"),
      retryConfig: Option[String] = None
  ) = {
    val resolvedRetryConfig = retryConfig.getOrElse {
      s"""
         |retry {
         |  max-attempts = $retryMaxAttempts
         |  base-delay = $retryBaseDelay
         |  max-delay = $retryMaxDelay
         |  jitter-ratio = $retryJitterRatio
         |}
         |""".stripMargin
    }

    ConfigFactory.parseString(
      s"""
         |daemon {
         |executors {
         |  default-executor {
         |    thread-count = $defaultExecutorThreadCount
         |    shutdown-grace-period = $defaultExecutorShutdownGracePeriod
         |  }
         |  database-executor {
         |    thread-count = $databaseExecutorThreadCount
         |    shutdown-grace-period = $databaseExecutorShutdownGracePeriod
         |  }
         |  io-dispatcher {
         |    thread-count = $ioDispatcherThreadCount
         |    shutdown-grace-period = $ioDispatcherShutdownGracePeriod
         |  }
         |}
         |jobs {
         |  spotify-access-token-refresh {
         |${spotifyAccessTokenRefreshEnabledConfig.map(line => line.linesIterator.map(configLine => s"    $configLine").mkString("\n")).getOrElse("")}
         |    interval = $interval
         |    timeout = $timeout
         |${resolvedRetryConfig.linesIterator.map(line => s"    $line").mkString("\n")}
         |    batch-size = $batchSize
         |  }
         |  followed-artists-sync-queue {
         |    enabled = true
         |    interval = 1h
         |    timeout = 5m
         |${resolvedRetryConfig.linesIterator.map(line => s"    $line").mkString("\n")}
         |  }
         |  followed-artists-sync {
         |    enabled = true
         |    interval = 1m
         |    timeout = 10m
         |${resolvedRetryConfig.linesIterator.map(line => s"    $line").mkString("\n")}
         |    batch-size = $followedSyncBatchSize
         |    processing-lease = $followedSyncProcessingLease
         |  }
         |  artist-release-sync-queue {
         |    enabled = true
         |    interval = 1h
         |    timeout = 5m
         |${resolvedRetryConfig.linesIterator.map(line => s"    $line").mkString("\n")}
         |  }
         |  artist-releases-sync {
         |    enabled = true
         |    interval = 1m
         |    timeout = 10m
         |${resolvedRetryConfig.linesIterator.map(line => s"    $line").mkString("\n")}
         |    batch-size = $artistReleasesSyncBatchSize
         |    processing-lease = $artistReleasesSyncProcessingLease
         |  }
         |  user-new-release-events-sync {
         |    enabled = true
         |    interval = 1m
         |    timeout = 5m
         |${resolvedRetryConfig.linesIterator.map(line => s"    $line").mkString("\n")}
         |    batch-size = $userNewReleaseEventsSyncBatchSize
         |  }
         |  user-new-release-notification-delivery-queue {
         |    enabled = true
         |    interval = 1m
         |    timeout = 10m
         |${resolvedRetryConfig.linesIterator.map(line => s"    $line").mkString("\n")}
         |    batch-size = $userNewReleaseNotificationDeliveryQueueBatchSize
         |    processing-lease = $userNewReleaseNotificationDeliveryQueueProcessingLease
         |  }
         |}
         |}
         |""".stripMargin
    )
  }
}
