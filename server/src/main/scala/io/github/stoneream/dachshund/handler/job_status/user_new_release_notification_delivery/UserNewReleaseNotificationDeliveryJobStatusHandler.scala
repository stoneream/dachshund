package io.github.stoneream.dachshund.handler.job_status.user_new_release_notification_delivery

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.action.TraceAction.TraceRequest
import io.github.stoneream.dachshund.handler.job_status.lib.{JobStatusFilterParser, JobStatusRenderer}
import io.github.stoneream.dachshund.handler.lib.{HandlerAuthPolicy, HandlerBase, HtmlRendererBase}
import io.github.stoneream.dachshund.lib.datetime.DateTimeService
import io.github.stoneream.dachshund.usecase.job_status.detail.{JobStatusDetailUseCaseException, JobStatusDetailUseCaseInput}
import io.github.stoneream.dachshund.usecase.job_status.user_new_release_notification_delivery.{UserNewReleaseNotificationDeliveryJobStatusUseCase, UserNewReleaseNotificationDeliveryJobStatusUseCaseOutput}
import play.api.mvc.{AnyContent, Result}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserNewReleaseNotificationDeliveryJobStatusHandler @Inject() (
    override val useCase: UserNewReleaseNotificationDeliveryJobStatusUseCase,
    dateTimeService: DateTimeService
)(using
    ec: ExecutionContext
) extends HandlerBase[
      TraceRequest[AnyContent],
      JobStatusDetailUseCaseInput,
      UserNewReleaseNotificationDeliveryJobStatusUseCaseOutput,
      JobStatusDetailUseCaseException,
      Result
    ] {
  override def authPolicy: HandlerAuthPolicy = HandlerAuthPolicy.LoginRequired

  override def handle(request: TraceRequest[AnyContent]): Future[JobStatusDetailUseCaseInput] =
    for {
      selectedStatuses <- validate("status")(request)(JobStatusFilterParser.selectedStatuses)
      detailPage <- validate("page")(request)(JobStatusFilterParser.selectedPage)
    } yield JobStatusDetailUseCaseInput(
      now = dateTimeService.now(),
      user = loggedInUser(request),
      selectedStatuses = selectedStatuses,
      detailPage = detailPage,
      detailLimit = JobStatusFilterParser.DetailLimit
    )

  override def renderer: HtmlRendererBase[UserNewReleaseNotificationDeliveryJobStatusUseCaseOutput, JobStatusDetailUseCaseException, Result] =
    JobStatusRenderer.renderer
}
