package io.github.stoneream.dachshund.daemon.module

import com.google.inject.{Guice, Injector}
import io.github.stoneream.dachshund.daemon.DaemonMain
import io.github.stoneream.dachshund.daemon.config.{ArtistReleaseSyncQueueJobConfig, ArtistReleasesSyncJobConfig, DaemonConfig, DaemonExecutorConfig, DaemonExecutorsConfig, DaemonJobsConfig, FollowedArtistsSyncJobConfig, FollowedArtistsSyncQueueJobConfig, JobName, JobRetryPolicy, JobSchedule, JobSetting, SpotifyAccessTokenRefreshJobConfig, UserNewReleaseEventsSyncJobConfig, UserNewReleaseNotificationDeliveryJobConfig}
import io.github.stoneream.dachshund.daemon.job.JobScheduler
import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor
import io.github.stoneream.dachshund.test.lib.config.TestApplicationConfig
import org.scalatest.featurespec.AnyFeatureSpec
import zio.{Exit, Runtime, UIO, Unsafe}

import scala.concurrent.duration.*
import scala.concurrent.{Await, Promise}

class DaemonModuleSpec extends AnyFeatureSpec {
  Feature("daemon module") {
    Scenario("Guice injector から scheduler graph を singleton として解決できる") {
      val injector = createInjector()
      try {
        val scheduler = injector.getInstance(classOf[JobScheduler])
        val sameScheduler = injector.getInstance(classOf[JobScheduler])

        assert(scheduler eq sameScheduler)
      } finally {
        close(injector)
      }
    }

    Scenario("Guice injector から daemon main を singleton として解決できる") {
      val injector = createInjector()
      try {
        val main = injector.getInstance(classOf[DaemonMain])
        val sameMain = injector.getInstance(classOf[DaemonMain])

        assert(main eq sameMain)
      } finally {
        close(injector)
      }
    }

    Scenario("default executor worker は JVM 終了を妨げない daemon thread として起動する") {
      val injector = createInjector()
      try {
        val executor = injector.getInstance(classOf[DefaultExecutor])
        val daemonThread = Promise[Boolean]()

        executor.execute(() => daemonThread.success(Thread.currentThread().isDaemon))

        assert(Await.result(daemonThread.future, 1.second))
      } finally {
        close(injector)
      }
    }
  }

  private def createInjector(): Injector =
    Guice.createInjector(
      new DaemonModule(
        applicationConfig = Some(TestApplicationConfig()),
        daemonConfig = Some(daemonConfig)
      )
    )

  private val daemonConfig: DaemonConfig =
    DaemonConfig(
      executors = DaemonExecutorsConfig(
        defaultExecutor = DaemonExecutorConfig(
          threadCount = 1,
          shutdownGracePeriod = 1.second
        ),
        databaseExecutor = DaemonExecutorConfig(
          threadCount = 1,
          shutdownGracePeriod = 1.second
        ),
        ioDispatcher = DaemonExecutorConfig(
          threadCount = 1,
          shutdownGracePeriod = 1.second
        )
      ),
      jobs = DaemonJobsConfig(
        spotifyAccessTokenRefresh = SpotifyAccessTokenRefreshJobConfig(
          setting = jobSetting("spotify-access-token-refresh"),
          batchSize = 1
        ),
        followedArtistsSyncQueue = FollowedArtistsSyncQueueJobConfig(
          setting = jobSetting("followed-artists-sync-queue")
        ),
        followedArtistsSync = FollowedArtistsSyncJobConfig(
          setting = jobSetting("followed-artists-sync"),
          batchSize = 1,
          processingLease = 1.minute
        ),
        artistReleaseSyncQueue = ArtistReleaseSyncQueueJobConfig(
          setting = jobSetting("artist-release-sync-queue")
        ),
        artistReleasesSync = ArtistReleasesSyncJobConfig(
          setting = jobSetting("artist-releases-sync"),
          batchSize = 1,
          processingLease = 1.minute
        ),
        userNewReleaseEventsSync = UserNewReleaseEventsSyncJobConfig(
          setting = jobSetting("user-new-release-events-sync"),
          batchSize = 1
        ),
        userNewReleaseNotificationDelivery = UserNewReleaseNotificationDeliveryJobConfig(
          setting = jobSetting("user-new-release-notification-delivery"),
          batchSize = 1,
          processingLease = 1.minute
        )
      )
    )

  private def jobSetting(name: String): JobSetting =
    JobSetting(
      name = JobName(name),
      enabled = true,
      schedule = JobSchedule.Every(1.minute),
      timeout = 1.minute,
      retryPolicy = JobRetryPolicy(
        maxAttempts = 1,
        baseDelay = 1.second,
        maxDelay = 1.second,
        jitterRatio = None
      )
    )

  private def close(injector: Injector): Unit =
    unsafeRun(injector.getInstance(classOf[DaemonLifecycle]).close())

  private def unsafeRun[A](task: UIO[A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(task) match {
        case Exit.Success(value) => value
        case Exit.Failure(cause) => throw cause.squash
      }
    }
}
