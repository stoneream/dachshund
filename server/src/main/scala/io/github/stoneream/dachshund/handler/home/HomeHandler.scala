package io.github.stoneream.dachshund.handler.home

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.action.TraceAction.TraceRequest
import io.github.stoneream.dachshund.handler.lib.{HandlerBase, HtmlRendererBase}
import io.github.stoneream.dachshund.lib.datetime.DateTimeService
import io.github.stoneream.dachshund.usecase.home.{HomeUseCase => UseCase, HomeUseCaseException => UseCaseException, HomeUseCaseInput => UseCaseInput, HomeUseCaseOutput => UseCaseOutput}
import play.api.mvc.{AnyContent, Result}

import scala.concurrent.Future

@Singleton
class HomeHandler @Inject() (
    override val useCase: UseCase,
    dateTimeService: DateTimeService
) extends HandlerBase[
      TraceRequest[AnyContent],
      UseCaseInput,
      UseCaseOutput,
      UseCaseException,
      Result
    ] {

  def handle(request: TraceRequest[AnyContent]): Future[UseCaseInput] =
    Future.successful {
      UseCaseInput(
        now = dateTimeService.now(),
        userSessionContext = request.userSessionContext
      )
    }

  override def renderer: HtmlRendererBase[UseCaseOutput, UseCaseException, Result] = HomeRenderer
}
