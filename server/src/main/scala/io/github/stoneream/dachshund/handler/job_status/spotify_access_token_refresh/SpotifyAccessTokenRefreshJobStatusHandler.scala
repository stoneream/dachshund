package io.github.stoneream.dachshund.handler.job_status.spotify_access_token_refresh

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.action.TraceAction.TraceRequest
import io.github.stoneream.dachshund.handler.job_status.{JobStatusFilterParser, JobStatusRenderer}
import io.github.stoneream.dachshund.handler.lib.{HandlerAuthPolicy, HandlerBase, HtmlRendererBase}
import io.github.stoneream.dachshund.lib.datetime.DateTimeService
import io.github.stoneream.dachshund.usecase.job_status.detail.{JobStatusDetailUseCaseException, JobStatusDetailUseCaseInput}
import io.github.stoneream.dachshund.usecase.job_status.spotify_access_token_refresh.{SpotifyAccessTokenRefreshJobStatusUseCase, SpotifyAccessTokenRefreshJobStatusUseCaseOutput}
import play.api.mvc.{AnyContent, Result}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SpotifyAccessTokenRefreshJobStatusHandler @Inject() (
    override val useCase: SpotifyAccessTokenRefreshJobStatusUseCase,
    dateTimeService: DateTimeService
)(using
    ec: ExecutionContext
) extends HandlerBase[
      TraceRequest[AnyContent],
      JobStatusDetailUseCaseInput,
      SpotifyAccessTokenRefreshJobStatusUseCaseOutput,
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

  override def renderer: HtmlRendererBase[SpotifyAccessTokenRefreshJobStatusUseCaseOutput, JobStatusDetailUseCaseException, Result] =
    JobStatusRenderer.renderer
}
