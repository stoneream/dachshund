package io.github.stoneream.dachshund.handler.job_status.lib

import io.github.stoneream.dachshund.handler.lib.{HtmlRendererBase, PageMeta}
import io.github.stoneream.dachshund.usecase.job_status.list.{JobStatusListUseCaseException, JobStatusListUseCaseOutput}
import play.api.mvc.{RequestHeader, Result}

object JobStatusIndexRenderer extends HtmlRendererBase[JobStatusListUseCaseOutput, JobStatusListUseCaseException, Result] {
  override def success(output: JobStatusListUseCaseOutput): Result =
    throw new UnsupportedOperationException("JobStatusIndexRenderer requires RequestHeader")

  override def success(output: JobStatusListUseCaseOutput, request: RequestHeader): Result = {
    given RequestHeader = request

    render(PageMeta(title = "Job status | Dachshund"))(
      views.html.job_status.index(output)
    )
  }

  override def failure(exception: JobStatusListUseCaseException): Result = throw exception
}
