package io.github.stoneream.dachshund.handler.lib

import io.github.stoneream.dachshund.action.TraceAction.TraceRequest
import io.github.stoneream.dachshund.auth.UserSessionContext
import io.github.stoneream.dachshund.controller.lib.ControllerError
import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
import io.github.stoneream.dachshund.usecase.UseCase

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait HandlerBase[
    Request <: TraceRequest[_],
    UseCaseInput,
    UseCaseOutput,
    UseCaseException,
    Response
] {
  val useCase: UseCase[UseCaseInput, UseCaseOutput, UseCaseException]
  def authPolicy: HandlerAuthPolicy = HandlerAuthPolicy.Public
  def handle(request: Request): Future[UseCaseInput]
  def renderer: HtmlRendererBase[UseCaseOutput, UseCaseException, Response]

  final def execute(
      request: Request
  )(using ExecutionContext, LoggingContext): Future[UseCaseOutput] = {
    for {
      _ <- authorize(request)
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

  protected final def loggedInUser(request: Request): UserSessionContext.NormalUser =
    request.userSessionContext match {
      case UserSessionContext.NotLoggedIn => throw ControllerError.LoginRequired
      case user: UserSessionContext.NormalUser => user
    }

  private def authorize(request: Request): Future[Unit] =
    authPolicy match {
      case HandlerAuthPolicy.Public =>
        Future.successful(())
      case HandlerAuthPolicy.LoginRequired =>
        request.userSessionContext match {
          case UserSessionContext.NotLoggedIn =>
            Future.failed(ControllerError.LoginRequired)
          case _: UserSessionContext.NormalUser =>
            Future.successful(())
        }
    }
}
