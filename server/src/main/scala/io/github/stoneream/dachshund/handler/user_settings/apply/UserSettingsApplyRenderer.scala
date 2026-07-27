package io.github.stoneream.dachshund.handler.user_settings.apply

import io.github.stoneream.dachshund.handler.lib.HtmlRendererBase
import io.github.stoneream.dachshund.usecase.user_settings.apply.{UserSettingsApplyUseCaseException as UseCaseException, UserSettingsApplyUseCaseOutput as UseCaseOutput}
import play.api.mvc.{Result, Results}

object UserSettingsApplyRenderer extends HtmlRendererBase[UseCaseOutput, UseCaseException, Result] {
  override def success(output: UseCaseOutput): Result =
    Results.SeeOther("/user-settings").flashing("success" -> "設定を保存しました。")

  override def failure(exception: UseCaseException): Result =
    exception match {
      case UseCaseException.SpotifyAuthorizationRequired(_) =>
        Results.SeeOther("/spotify/auth/login").flashing("error" -> "Spotify の再認可が必要です。")
      case UseCaseException.SpotifyAuthorizationTemporarilyUnavailable(_) =>
        Results.SeeOther("/user-settings").flashing("error" -> "Spotify 認可情報を確認できませんでした。時間をおいて再度お試しください。")
      case UseCaseException.PlaylistSetupFailed(_) =>
        Results.SeeOther("/user-settings").flashing("error" -> "新着リリース playlist 設定を保存できませんでした。")
    }
}
