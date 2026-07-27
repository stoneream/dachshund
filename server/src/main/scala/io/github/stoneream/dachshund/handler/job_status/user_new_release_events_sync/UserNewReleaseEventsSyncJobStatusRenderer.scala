package io.github.stoneream.dachshund.handler.job_status.user_new_release_events_sync

import io.github.stoneream.dachshund.handler.lib.{HtmlRendererBase, PageMeta}
import io.github.stoneream.dachshund.usecase.job_status.detail.JobStatusDetailUseCaseException
import io.github.stoneream.dachshund.usecase.job_status.user_new_release_events_sync.UserNewReleaseEventsSyncJobStatusUseCaseOutput
import play.api.mvc.{RequestHeader, Result}

class UserNewReleaseEventsSyncJobStatusRenderer
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
