package io.github.stoneream.dachshund.controller

import io.github.stoneream.dachshund.action.TraceAction
import io.github.stoneream.dachshund.action.TraceAction.TraceRequest
import io.github.stoneream.dachshund.controller.lib.ControllerBaseImpl
import io.github.stoneream.dachshund.handler.home.HomeHandler
import play.api.mvc.*

import com.google.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class HomeController @Inject() (
    cc: ControllerComponents,
    traceAction: TraceAction,
    homeHandler: HomeHandler
) extends AbstractController(cc)
    with ControllerBaseImpl {
  private given ExecutionContext = cc.executionContext

  def index(): Action[AnyContent] = traceAction.async { implicit request: TraceRequest[AnyContent] =>
    handle(homeHandler)(request)
  }
}
