package io.github.stoneream.dachshund.model

enum ReleaseNotificationType(val dbValue: String) {
  case Playlist extends ReleaseNotificationType("PLAYLIST")
}

object ReleaseNotificationType {
  def fromDbValue(value: String): ReleaseNotificationType =
    values
      .find(_.dbValue == value)
      .getOrElse(throw IllegalArgumentException(s"リリース通知種別が想定外です: $value"))
}
