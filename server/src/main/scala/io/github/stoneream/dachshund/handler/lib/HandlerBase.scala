package io.github.stoneream.dachshund.handler.lib

import io.github.stoneream.dachshund.controller.lib.ControllerError
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.usecase.UseCase

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait HandlerBase[
    Request,
    UseCaseInput,
    UseCaseOutput,
    UseCaseException,
    Response
] {
  val useCase: UseCase[UseCaseInput, UseCaseOutput, UseCaseException]
  def handle(request: Request): Future[UseCaseInput]
  def renderer: HtmlRendererBase[UseCaseOutput, UseCaseException, Response]

  final def execute(
      request: Request
  )(using ExecutionContext, LoggingContext): Future[UseCaseOutput] = {
    for {
      input <- handle(request)
      output <- useCase.run(input)
    } yield output
  }

  def validate[A, B](name: String)(a: A)(f: A => B): Future[B] = {
    Try(f(a)).fold(
      e => Future.failed(ControllerError.InvalidParameter(name, e)),
      b => Future.successful(b)
    )
  }

  protected final def requiredValue[A](name: String)(value: Option[A]): A =
    value.getOrElse {
      throw ControllerError.InvalidParameter(
        param = name,
        e = new IllegalArgumentException(s"必須パラメーター $name が存在しません")
      )
    }
}
