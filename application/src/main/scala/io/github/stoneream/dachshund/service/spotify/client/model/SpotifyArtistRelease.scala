package io.github.stoneream.dachshund.service.spotify.client.model

import java.time.LocalDateTime

final case class SpotifyArtistRelease(
    spotifyReleaseCode: String,
    sourceSpotifyArtistCode: String,
    releaseName: String,
    releaseType: String,
    albumType: String,
    albumGroup: Option[String],
    spotifyReleaseUri: String,
    spotifyUrl: String,
    href: String,
    primaryImageUrl: String,
    primaryImageHeight: Option[Int],
    primaryImageWidth: Option[Int],
    imagesJson: Option[String],
    releaseDateText: String,
    releaseDatePrecision: String,
    releaseDateAt: Option[LocalDateTime],
    totalTracksCount: Option[Int],
    labelName: Option[String],
    normalizedLabelName: Option[String],
    externalIdsJson: Option[String],
    upcCode: Option[String],
    eanCode: Option[String],
    isrcCode: Option[String],
    copyrightsJson: Option[String],
    availableMarketsJson: Option[String],
    genresJson: Option[String],
    restrictionsJson: Option[String],
    popularity: Option[Int],
    tracks: Seq[SpotifyReleaseTrack]
)
