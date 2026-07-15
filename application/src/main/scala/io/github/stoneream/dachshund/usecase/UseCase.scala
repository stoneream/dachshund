package io.github.stoneream.dachshund.usecase

import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext

import scala.concurrent.Future

trait UseCase[UseCaseInput, UseCaseOutput, UseCaseException] {
  def run(input: UseCaseInput)(using LoggingContext): Future[UseCaseOutput]
}
