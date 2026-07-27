package io.github.stoneream.dachshund.handler.job_status.followed_artists_sync

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.action.TraceAction.TraceRequest
import io.github.stoneream.dachshund.handler.job_status.lib.{JobStatusFilterParser, JobStatusRenderer}
import io.github.stoneream.dachshund.handler.lib.{HandlerAuthPolicy, HandlerBase, HtmlRendererBase}
import io.github.stoneream.dachshund.lib.datetime.DateTimeService
import io.github.stoneream.dachshund.usecase.job_status.detail.{JobStatusDetailUseCaseException, JobStatusDetailUseCaseInput}
import io.github.stoneream.dachshund.usecase.job_status.followed_artists_sync.{FollowedArtistsSyncJobStatusUseCase, FollowedArtistsSyncJobStatusUseCaseOutput}
import play.api.mvc.{AnyContent, Result}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FollowedArtistsSyncJobStatusHandler @Inject() (
    override val useCase: FollowedArtistsSyncJobStatusUseCase,
    dateTimeService: DateTimeService
)(using
    ec: ExecutionContext
) extends HandlerBase[
      TraceRequest[AnyContent],
      JobStatusDetailUseCaseInput,
      FollowedArtistsSyncJobStatusUseCaseOutput,
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

  override def renderer: HtmlRendererBase[FollowedArtistsSyncJobStatusUseCaseOutput, JobStatusDetailUseCaseException, Result] =
    JobStatusRenderer.renderer
}
