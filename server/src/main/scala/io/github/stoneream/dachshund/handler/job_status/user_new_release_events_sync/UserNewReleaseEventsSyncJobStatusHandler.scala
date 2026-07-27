package io.github.stoneream.dachshund.handler.job_status.user_new_release_events_sync

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.action.TraceAction.TraceRequest
import io.github.stoneream.dachshund.handler.job_status.JobStatusFilterParser
import io.github.stoneream.dachshund.handler.lib.{HandlerAuthPolicy, HandlerBase, HtmlRendererBase, PageMeta}
import io.github.stoneream.dachshund.usecase.job_status.detail.JobStatusDetailUseCaseException
import io.github.stoneream.dachshund.usecase.job_status.user_new_release_events_sync.{UserNewReleaseEventsSyncJobStatusUseCase, UserNewReleaseEventsSyncJobStatusUseCaseInput, UserNewReleaseEventsSyncJobStatusUseCaseOutput}
import play.api.mvc.{AnyContent, RequestHeader, Result}

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
    UserNewReleaseEventsSyncJobStatusRenderer
}

object UserNewReleaseEventsSyncJobStatusRenderer
    extends HtmlRendererBase[UserNewReleaseEventsSyncJobStatusUseCaseOutput, JobStatusDetailUseCaseException, Result] {
  override def success(output: UserNewReleaseEventsSyncJobStatusUseCaseOutput): Result =
    throw new UnsupportedOperationException("UserNewReleaseEventsSyncJobStatusRenderer requires RequestHeader")

  override def success(output: UserNewReleaseEventsSyncJobStatusUseCaseOutput, request: RequestHeader): Result = {
    given RequestHeader = request

    render(PageMeta(title = s"${output.context.currentJob.title} | Job status | Dachshund"))(
      views.html.job_status.userNewReleaseEventsSync(output.context)
    )
  }

  override def failure(exception: JobStatusDetailUseCaseException): Result = throw exception
}
