package io.github.stoneream.dachshund.usecase.user_settings.apply

import io.github.stoneream.dachshund.auth.UserSessionContext
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime

final case class UserSettingsApplyUseCaseInput(
    now: BusinessDateTime,
    userSessionContext: UserSessionContext,
    newReleasePlaylistEnabled: Boolean
)
