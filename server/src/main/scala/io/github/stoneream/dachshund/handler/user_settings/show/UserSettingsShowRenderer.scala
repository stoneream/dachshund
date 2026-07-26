package io.github.stoneream.dachshund.handler.user_settings.show

import io.github.stoneream.dachshund.handler.lib.{HtmlRendererBase, PageMeta}
import io.github.stoneream.dachshund.usecase.user_settings.show.{UserSettingsShowUseCaseException as UseCaseException, UserSettingsShowUseCaseOutput as UseCaseOutput}
import play.api.mvc.{RequestHeader, Result, Results}

object UserSettingsShowRenderer extends HtmlRendererBase[UseCaseOutput, UseCaseException, Result] {
  override def success(output: UseCaseOutput): Result =
    throw new UnsupportedOperationException("UserSettingsShowRenderer requires RequestHeader")

  override def success(output: UseCaseOutput, request: RequestHeader): Result = {
    given RequestHeader = request

    render(PageMeta(title = "User settings | Dachshund"))(
      views.html.user_settings.index(output)
    )
  }

  override def failure(exception: UseCaseException): Result =
    exception match {
      case UseCaseException.NotLoggedIn =>
        Results.SeeOther("/spotify/auth/login")
    }
}
