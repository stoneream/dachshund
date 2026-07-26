package io.github.stoneream.dachshund.handler.lib

import play.api.mvc.{RequestHeader, Result, Results}
import play.twirl.api.Html

trait HtmlRendererBase[
    UseCaseOutput,
    UseCaseException,
    Response
] {
  protected final def render(pageMeta: PageMeta)(body: Html): Result =
    Results.Ok(views.html.main(pageMeta)(body))

  protected final def render(status: Results.Status, pageMeta: PageMeta)(body: Html): Result =
    status(views.html.main(pageMeta)(body))

  def success(output: UseCaseOutput): Result

  def success(output: UseCaseOutput, request: RequestHeader): Result = {
    val _ = request
    success(output)
  }

  def failure(exception: UseCaseException): Result

  def failure(exception: UseCaseException, request: RequestHeader): Result = {
    val _ = request
    failure(exception)
  }
}
