package io.github.stoneream.dachshund.controller

import com.google.inject.{Inject, Singleton}
import io.github.stoneream.dachshund.action.TraceAction
import io.github.stoneream.dachshund.action.TraceAction.TraceRequest
import io.github.stoneream.dachshund.controller.lib.ControllerBaseImpl
import io.github.stoneream.dachshund.handler.user_settings.apply.UserSettingsApplyHandler
import io.github.stoneream.dachshund.handler.user_settings.show.UserSettingsShowHandler
import play.api.mvc.*
import play.filters.csrf.CSRFAddToken

import scala.concurrent.ExecutionContext

@Singleton
class UserSettingsController @Inject() (
    cc: ControllerComponents,
    traceAction: TraceAction,
    userSettingsShowHandler: UserSettingsShowHandler,
    userSettingsApplyHandler: UserSettingsApplyHandler,
    addToken: CSRFAddToken
) extends AbstractController(cc)
    with ControllerBaseImpl {
  private given ExecutionContext = cc.executionContext

  def index(): Action[AnyContent] = addToken {
    traceAction.async { implicit request: TraceRequest[AnyContent] =>
      handle(userSettingsShowHandler)(request)
    }
  }

  def save(): Action[AnyContent] = traceAction.async { implicit request: TraceRequest[AnyContent] =>
    handle(userSettingsApplyHandler)(request)
  }
}
