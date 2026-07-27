package io.github.stoneream.dachshund.usecase.user_settings.show

import io.github.stoneream.dachshund.auth.UserSessionContext
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime

final case class UserSettingsShowUseCaseInput(
    now: BusinessDateTime,
    user: UserSessionContext.NormalUser,
    successMessage: Option[String],
    errorMessage: Option[String]
)
