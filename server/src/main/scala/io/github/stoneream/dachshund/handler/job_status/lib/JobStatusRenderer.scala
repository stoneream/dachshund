package io.github.stoneream.dachshund.handler.job_status.lib

import io.github.stoneream.dachshund.handler.lib.{HtmlRendererBase, PageMeta}
import io.github.stoneream.dachshund.usecase.job_status.detail.{JobStatusDetailUseCaseException, JobStatusDetailUseCaseOutput}
import play.api.mvc.{RequestHeader, Result}

object JobStatusRenderer {
  def renderer[Output <: JobStatusDetailUseCaseOutput]: HtmlRendererBase[Output, JobStatusDetailUseCaseException, Result] =
    new HtmlRendererBase[Output, JobStatusDetailUseCaseException, Result] {
      override def success(output: Output): Result =
        throw new UnsupportedOperationException("JobStatusRenderer requires RequestHeader")

      override def success(output: Output, request: RequestHeader): Result = {
        given RequestHeader = request

        render(PageMeta(title = s"${output.context.currentJob.title} | Job status | Dachshund"))(
          views.html.job_status.detail(output.context)
        )
      }

      override def failure(exception: JobStatusDetailUseCaseException): Result = throw exception
    }
}
