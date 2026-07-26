package io.github.stoneream.dachshund.model

enum PlaylistUsageType(val dbValue: String) {
  case NewReleaseNotification extends PlaylistUsageType("NEW_RELEASE_NOTIFICATION")
}

object PlaylistUsageType {
  def fromDbValue(value: String): PlaylistUsageType =
    values
      .find(_.dbValue == value)
      .getOrElse(throw IllegalArgumentException(s"プレイリスト用途種別が想定外です: $value"))
}
