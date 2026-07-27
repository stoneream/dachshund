package io.github.stoneream.dachshund.usecase.job_status.list

abstract sealed class JobStatusListUseCaseException(
    override val getMessage: String,
    cause: Throwable = null
) extends Exception(getMessage, cause)

object JobStatusListUseCaseException
