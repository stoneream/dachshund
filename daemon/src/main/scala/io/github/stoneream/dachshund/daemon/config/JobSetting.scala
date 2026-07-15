package io.github.stoneream.dachshund.daemon.config

import scala.concurrent.duration.FiniteDuration

final case class JobSetting(
    name: JobName,
    schedule: JobSchedule,
    timeout: FiniteDuration,
    retryPolicy: JobRetryPolicy
)
