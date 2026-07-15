package io.github.stoneream.dachshund.handler.home

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.action.TraceAction.TraceRequest
import io.github.stoneream.dachshund.handler.lib.{HandlerBase, HtmlRendererBase, UserContextResolver}
import io.github.stoneream.dachshund.lib.datetime.DateTimeService
import io.github.stoneream.dachshund.usecase.home.{HomeUseCase => UseCase, HomeUseCaseException => UseCaseException, HomeUseCaseInput => UseCaseInput, HomeUseCaseOutput => UseCaseOutput}
import play.api.mvc.{AnyContent, Result}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HomeHandler @Inject() (
    override val useCase: UseCase,
    dateTimeService: DateTimeService,
    userContextResolver: UserContextResolver
)(using
    ExecutionContext
) extends HandlerBase[
      TraceRequest[AnyContent],
      UseCaseInput,
      UseCaseOutput,
      UseCaseException,
      Result
    ] {

  def handle(request: TraceRequest[AnyContent]): Future[UseCaseInput] =
    for {
      now <- Future.successful(dateTimeService.now())
      userSessionContext <- userContextResolver.resolve(request, now)(using request.loggingContext)
    } yield {
      UseCaseInput(
        now = now,
        userSessionContext = userSessionContext
      )
    }

  override def renderer: HtmlRendererBase[UseCaseOutput, UseCaseException, Result] = HomeRenderer
}
