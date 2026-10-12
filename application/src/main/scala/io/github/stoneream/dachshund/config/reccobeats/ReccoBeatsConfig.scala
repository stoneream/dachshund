package io.github.stoneream.dachshund.config.reccobeats

import pureconfig.ConfigReader

import scala.concurrent.duration.FiniteDuration

final case class ReccoBeatsConfig(
    apiBaseUrl: String,
    connectTimeout: FiniteDuration,
    requestTimeout: FiniteDuration
) derives ConfigReader
