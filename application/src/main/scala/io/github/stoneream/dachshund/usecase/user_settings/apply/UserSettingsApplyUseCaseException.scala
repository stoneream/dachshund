package io.github.stoneream.dachshund.usecase.user_settings.apply

abstract sealed class UserSettingsApplyUseCaseException(
    override val getMessage: String,
    cause: Throwable = null
) extends Exception(getMessage, cause)

object UserSettingsApplyUseCaseException {
  final case class SpotifyAuthorizationRequired(causeException: Throwable) extends UserSettingsApplyUseCaseException("Spotify の再認可が必要です", causeException)

  final case class SpotifyAuthorizationTemporarilyUnavailable(causeException: Throwable)
      extends UserSettingsApplyUseCaseException("Spotify access token を一時的に解決できません", causeException)

  final case class PlaylistSetupFailed(causeException: Throwable) extends UserSettingsApplyUseCaseException("新着リリース playlist 設定の適用に失敗しました", causeException)
}
