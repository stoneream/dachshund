package io.github.stoneream.dachshund.usecase.home

import io.github.stoneream.dachshund.auth.UserSessionContext
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime

final case class HomeUseCaseInput(
    now: BusinessDateTime,
    userSessionContext: UserSessionContext
)
