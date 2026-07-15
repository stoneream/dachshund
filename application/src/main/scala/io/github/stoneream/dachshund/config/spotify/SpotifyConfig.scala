package io.github.stoneream.dachshund.config.spotify

import pureconfig.ConfigReader

final case class SpotifyConfig(
    client: SpotifyClientConfig,
    token: SpotifyTokenConfig
) derives ConfigReader
