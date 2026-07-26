package io.github.stoneream.dachshund.usecase.user_settings.show

import io.github.stoneream.dachshund.auth.UserSessionContext
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime

final case class UserSettingsShowUseCaseInput(
    now: BusinessDateTime,
    userSessionContext: UserSessionContext,
    successMessage: Option[String],
    errorMessage: Option[String]
)
