package io.github.stoneream.dachshund.handler.user_settings.show

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.action.TraceAction.TraceRequest
import io.github.stoneream.dachshund.handler.lib.{HandlerAuthPolicy, HandlerBase, HtmlRendererBase}
import io.github.stoneream.dachshund.lib.datetime.DateTimeService
import io.github.stoneream.dachshund.usecase.user_settings.show.{UserSettingsShowUseCase as UseCase, UserSettingsShowUseCaseException as UseCaseException, UserSettingsShowUseCaseInput as UseCaseInput, UserSettingsShowUseCaseOutput as UseCaseOutput}
import play.api.mvc.{AnyContent, Result}

import scala.concurrent.Future

@Singleton
class UserSettingsShowHandler @Inject() (
    override val useCase: UseCase,
    dateTimeService: DateTimeService
) extends HandlerBase[
      TraceRequest[AnyContent],
      UseCaseInput,
      UseCaseOutput,
      UseCaseException,
      Result
    ] {
  override def authPolicy: HandlerAuthPolicy = HandlerAuthPolicy.LoginRequired

  override def handle(request: TraceRequest[AnyContent]): Future[UseCaseInput] =
    Future.successful(
      UseCaseInput(
        now = dateTimeService.now(),
        user = loggedInUser(request),
        successMessage = request.flash.get("success"),
        errorMessage = request.flash.get("error")
      )
    )

  override def renderer: HtmlRendererBase[UseCaseOutput, UseCaseException, Result] = UserSettingsShowRenderer
}
