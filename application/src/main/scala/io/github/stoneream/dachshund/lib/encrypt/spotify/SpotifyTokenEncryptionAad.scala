package io.github.stoneream.dachshund.lib.encrypt.spotify

object SpotifyTokenEncryptionAad {
  def accessToken(userId: Long, keyVersion: String): String =
    aad(userId, "access_token", keyVersion)

  def refreshToken(userId: Long, keyVersion: String): String =
    aad(userId, "refresh_token", keyVersion)

  private def aad(userId: Long, tokenKind: String, keyVersion: String): String =
    s"spotify_authorizations:$userId:$tokenKind:$keyVersion"
}
