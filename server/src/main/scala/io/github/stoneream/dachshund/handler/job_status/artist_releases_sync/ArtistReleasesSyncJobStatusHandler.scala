package io.github.stoneream.dachshund.handler.job_status.artist_releases_sync

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.action.TraceAction.TraceRequest
import io.github.stoneream.dachshund.handler.job_status.{JobStatusFilterParser, JobStatusRenderer}
import io.github.stoneream.dachshund.handler.lib.{HandlerAuthPolicy, HandlerBase, HtmlRendererBase}
import io.github.stoneream.dachshund.lib.datetime.DateTimeService
import io.github.stoneream.dachshund.usecase.job_status.detail.{JobStatusDetailUseCaseException, JobStatusDetailUseCaseInput}
import io.github.stoneream.dachshund.usecase.job_status.artist_releases_sync.{ArtistReleasesSyncJobStatusUseCase, ArtistReleasesSyncJobStatusUseCaseOutput}
import play.api.mvc.{AnyContent, Result}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ArtistReleasesSyncJobStatusHandler @Inject() (
    override val useCase: ArtistReleasesSyncJobStatusUseCase,
    dateTimeService: DateTimeService
)(using
    ec: ExecutionContext
) extends HandlerBase[
      TraceRequest[AnyContent],
      JobStatusDetailUseCaseInput,
      ArtistReleasesSyncJobStatusUseCaseOutput,
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

  override def renderer: HtmlRendererBase[ArtistReleasesSyncJobStatusUseCaseOutput, JobStatusDetailUseCaseException, Result] =
    JobStatusRenderer.renderer
}
