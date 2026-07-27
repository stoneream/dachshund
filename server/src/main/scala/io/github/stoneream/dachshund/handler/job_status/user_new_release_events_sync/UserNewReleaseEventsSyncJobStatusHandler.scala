package io.github.stoneream.dachshund.handler.job_status.user_new_release_events_sync

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.action.TraceAction.TraceRequest
import io.github.stoneream.dachshund.handler.job_status.lib.JobStatusFilterParser
import io.github.stoneream.dachshund.handler.lib.{HandlerAuthPolicy, HandlerBase, HtmlRendererBase}
import io.github.stoneream.dachshund.usecase.job_status.detail.JobStatusDetailUseCaseException
import io.github.stoneream.dachshund.usecase.job_status.user_new_release_events_sync.{UserNewReleaseEventsSyncJobStatusUseCase, UserNewReleaseEventsSyncJobStatusUseCaseInput, UserNewReleaseEventsSyncJobStatusUseCaseOutput}
import play.api.mvc.{AnyContent, Result}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserNewReleaseEventsSyncJobStatusHandler @Inject() (
    override val useCase: UserNewReleaseEventsSyncJobStatusUseCase
)(using
    ec: ExecutionContext
) extends HandlerBase[
      TraceRequest[AnyContent],
      UserNewReleaseEventsSyncJobStatusUseCaseInput,
      UserNewReleaseEventsSyncJobStatusUseCaseOutput,
      JobStatusDetailUseCaseException,
      Result
    ] {
  override def authPolicy: HandlerAuthPolicy = HandlerAuthPolicy.LoginRequired

  override def handle(request: TraceRequest[AnyContent]): Future[UserNewReleaseEventsSyncJobStatusUseCaseInput] =
    for {
      _ <- validate("status")(request)(JobStatusFilterParser.rejectStatuses)
      detailPage <- validate("page")(request)(JobStatusFilterParser.selectedPage)
    } yield UserNewReleaseEventsSyncJobStatusUseCaseInput(
      user = loggedInUser(request),
      detailPage = detailPage,
      detailLimit = JobStatusFilterParser.DetailLimit
    )

  override def renderer: HtmlRendererBase[UserNewReleaseEventsSyncJobStatusUseCaseOutput, JobStatusDetailUseCaseException, Result] =
    new UserNewReleaseEventsSyncJobStatusRenderer
}
