package io.github.stoneream.dachshund.usecase.user_settings

import io.github.stoneream.dachshund.model.PlaylistUsageType

private[user_settings] object UserSettingsManagedPlaylist {
  val BaseName = "Dachshund Radar"
  val UsageType: PlaylistUsageType = PlaylistUsageType.NewReleaseNotification
}
