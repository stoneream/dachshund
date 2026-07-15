package io.github.stoneream.dachshund.handler.home

import io.github.stoneream.dachshund.handler.lib.{HtmlRendererBase, PageMeta}
import io.github.stoneream.dachshund.usecase.home.{HomeUseCaseException => UseCaseException, HomeUseCaseOutput => UseCaseOutput}
import play.api.mvc.{Result, Results}

object HomeRenderer extends HtmlRendererBase[UseCaseOutput, UseCaseException, Result] {
  override def success(output: UseCaseOutput): Result = render(
    PageMeta(
      title = "Dachshund"
    )
  )(views.html.index(Right(output)))

  override def failure(exception: UseCaseException): Result = render(
    Results.InternalServerError,
    PageMeta(
      title = "Dachshund"
    )
  )(views.html.index(Left(exception)))
}
