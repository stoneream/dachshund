package io.github.stoneream.dachshund.usecase.user_settings.show

abstract sealed class UserSettingsShowUseCaseException(
    override val getMessage: String,
    cause: Throwable = null
) extends Exception(getMessage, cause)

object UserSettingsShowUseCaseException
