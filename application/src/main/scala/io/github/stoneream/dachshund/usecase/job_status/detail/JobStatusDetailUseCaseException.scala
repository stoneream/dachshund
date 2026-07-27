package io.github.stoneream.dachshund.usecase.job_status.detail

abstract sealed class JobStatusDetailUseCaseException(
    override val getMessage: String,
    cause: Throwable = null
) extends Exception(getMessage, cause)

object JobStatusDetailUseCaseException
