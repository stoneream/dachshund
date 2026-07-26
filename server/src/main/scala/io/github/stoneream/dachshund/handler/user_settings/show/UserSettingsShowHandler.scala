package io.github.stoneream.dachshund.handler.user_settings.show

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.action.TraceAction.TraceRequest
import io.github.stoneream.dachshund.handler.lib.{HandlerBase, HtmlRendererBase, UserContextResolver}
import io.github.stoneream.dachshund.lib.datetime.DateTimeService
import io.github.stoneream.dachshund.usecase.user_settings.show.{UserSettingsShowUseCase as UseCase, UserSettingsShowUseCaseException as UseCaseException, UserSettingsShowUseCaseInput as UseCaseInput, UserSettingsShowUseCaseOutput as UseCaseOutput}
import play.api.mvc.{AnyContent, Result}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserSettingsShowHandler @Inject() (
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
  override def handle(request: TraceRequest[AnyContent]): Future[UseCaseInput] =
    for {
      now <- Future.successful(dateTimeService.now())
      userSessionContext <- userContextResolver.resolve(request, now)(using request.loggingContext)
    } yield UseCaseInput(
      now = now,
      userSessionContext = userSessionContext,
      successMessage = request.flash.get("success"),
      errorMessage = request.flash.get("error")
    )

  override def renderer: HtmlRendererBase[UseCaseOutput, UseCaseException, Result] = UserSettingsShowRenderer
}
