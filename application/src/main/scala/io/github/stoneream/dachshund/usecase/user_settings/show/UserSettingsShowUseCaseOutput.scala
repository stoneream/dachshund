package io.github.stoneream.dachshund.usecase.user_settings.show

final case class UserSettingsShowUseCaseOutput(
    user: UserSettingsShowUseCaseOutput.ViewUser,
    newReleasePlaylistEnabled: Boolean,
    playlistName: String,
    successMessage: Option[String],
    errorMessage: Option[String]
)

object UserSettingsShowUseCaseOutput {
  final case class ViewUser(
      displayName: String
  )
}
