package io.github.stoneream.dachshund.service.spotify.client.api.spotify_search_track.model

final case class SpotifyTrackSearchPage(
    tracks: Seq[SpotifyTrackSearchItem],
    total: Int,
    offset: Int,
    limit: Int,
    nextOffset: Option[Int]
) {
  def previousOffset: Option[Int] = Option.when(offset > 0)(math.max(0, offset - limit))
}

object SpotifyTrackSearchPage {
  val PageSize = 10
  val MaxOffset = 1000
}
