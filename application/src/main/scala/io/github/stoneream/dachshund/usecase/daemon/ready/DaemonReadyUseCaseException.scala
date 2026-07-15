package io.github.stoneream.dachshund.usecase.daemon.ready

sealed abstract class DaemonReadyUseCaseException(
    message: String,
    cause: Throwable = null
) extends Exception(message, cause)

object DaemonReadyUseCaseException {
  final case class Unavailable(causeException: Throwable) extends DaemonReadyUseCaseException("daemon readiness check failed", causeException)
}
