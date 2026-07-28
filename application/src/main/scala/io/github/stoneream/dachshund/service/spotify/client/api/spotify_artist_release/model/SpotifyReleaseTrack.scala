package io.github.stoneream.dachshund.service.spotify.client.api.spotify_artist_release.model

final case class SpotifyReleaseTrack(
    spotifyTrackCode: String,
    trackName: String,
    spotifyTrackUri: String,
    spotifyUrl: String,
    href: String,
    discNumber: Int,
    trackNumber: Int,
    durationMs: Option[Int],
    explicit: Option[Long],
    isPlayable: Option[Long],
    isLocal: Option[Long],
    linkedFromSpotifyTrackCode: Option[String],
    linkedFromSpotifyTrackUri: Option[String],
    previewUrl: Option[String],
    externalIdsJson: Option[String],
    isrcCode: Option[String],
    eanCode: Option[String],
    upcCode: Option[String],
    availableMarketsJson: Option[String],
    restrictionsJson: Option[String],
    popularity: Option[Int]
)
