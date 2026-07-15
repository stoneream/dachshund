package io.github.stoneream.dachshund.usecase.home

abstract sealed class HomeUseCaseException(
    override val getMessage: String,
    cause: Throwable = null
) extends Exception(getMessage, cause)

object HomeUseCaseException {
  final case class Unknown(message: String, causeException: Throwable) extends HomeUseCaseException(message, causeException)
}
