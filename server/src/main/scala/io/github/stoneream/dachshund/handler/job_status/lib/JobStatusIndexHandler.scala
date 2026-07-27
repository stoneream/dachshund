package io.github.stoneream.dachshund.handler.job_status.lib

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.action.TraceAction.TraceRequest
import io.github.stoneream.dachshund.handler.lib.{HandlerAuthPolicy, HandlerBase, HtmlRendererBase}
import io.github.stoneream.dachshund.usecase.job_status.list.{JobStatusListUseCase, JobStatusListUseCaseException, JobStatusListUseCaseInput, JobStatusListUseCaseOutput}
import play.api.mvc.{AnyContent, Result}

import scala.concurrent.Future

@Singleton
class JobStatusIndexHandler @Inject() (
    override val useCase: JobStatusListUseCase
) extends HandlerBase[
      TraceRequest[AnyContent],
      JobStatusListUseCaseInput,
      JobStatusListUseCaseOutput,
      JobStatusListUseCaseException,
      Result
    ] {
  override def authPolicy: HandlerAuthPolicy = HandlerAuthPolicy.LoginRequired

  override def handle(request: TraceRequest[AnyContent]): Future[JobStatusListUseCaseInput] =
    Future.successful(
      JobStatusListUseCaseInput(
        user = loggedInUser(request)
      )
    )

  override def renderer: HtmlRendererBase[JobStatusListUseCaseOutput, JobStatusListUseCaseException, Result] =
    JobStatusIndexRenderer
}
