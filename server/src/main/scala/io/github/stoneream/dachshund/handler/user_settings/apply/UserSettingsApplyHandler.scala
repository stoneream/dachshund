package io.github.stoneream.dachshund.handler.user_settings.apply

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.action.TraceAction.TraceRequest
import io.github.stoneream.dachshund.handler.lib.{HandlerAuthPolicy, HandlerBase, HtmlRendererBase}
import io.github.stoneream.dachshund.lib.datetime.DateTimeService
import io.github.stoneream.dachshund.usecase.user_settings.apply.{UserSettingsApplyUseCase as UseCase, UserSettingsApplyUseCaseException as UseCaseException, UserSettingsApplyUseCaseInput as UseCaseInput, UserSettingsApplyUseCaseOutput as UseCaseOutput}
import play.api.mvc.{AnyContent, Result}

import scala.concurrent.Future

@Singleton
class UserSettingsApplyHandler @Inject() (
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
        newReleasePlaylistEnabled = formValue(request, "newReleasePlaylistEnabled").contains("true")
      )
    )

  override def renderer: HtmlRendererBase[UseCaseOutput, UseCaseException, Result] = UserSettingsApplyRenderer

  private def formValue(request: TraceRequest[AnyContent], name: String): Option[String] =
    request.body.asFormUrlEncoded.flatMap(_.get(name).flatMap(_.headOption))
}
