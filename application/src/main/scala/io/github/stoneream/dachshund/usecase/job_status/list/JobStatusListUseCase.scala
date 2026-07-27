package io.github.stoneream.dachshund.usecase.job_status.list

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.usecase.UseCase

import scala.concurrent.Future

@Singleton
class JobStatusListUseCase @Inject() ()
    extends UseCase[
      JobStatusListUseCaseInput,
      JobStatusListUseCaseOutput,
      JobStatusListUseCaseException
    ] {
  override def run(input: JobStatusListUseCaseInput)(using LoggingContext): Future[JobStatusListUseCaseOutput] =
    Future.successful(
      JobStatusListUseCaseOutput.build(
        userDisplayName = input.user.displayName
      )
    )
}
