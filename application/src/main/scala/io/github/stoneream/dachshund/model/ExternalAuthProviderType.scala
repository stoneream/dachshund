package io.github.stoneream.dachshund.model

enum ExternalAuthProviderType(val dbValue: String) {
  case Spotify extends ExternalAuthProviderType("SPOTIFY")
}

object ExternalAuthProviderType {
  def fromDbValue(dbValue: String): ExternalAuthProviderType =
    values
      .find(_.dbValue == dbValue)
      .getOrElse(throw IllegalArgumentException(s"外部認証プロバイダー種別が想定外です: $dbValue"))
}
