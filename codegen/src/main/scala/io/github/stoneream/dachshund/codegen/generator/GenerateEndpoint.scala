package io.github.stoneream.dachshund.codegen.generator

import io.github.stoneream.dachshund.codegen.NamedPathRequest
import io.github.stoneream.dachshund.codegen.model.{GeneratedArtifacts, GeneratedFile, GeneratedSnippet}
import io.github.stoneream.dachshund.codegen.util.Naming

import java.nio.file.Path

object GenerateEndpoint {
  def render(
      request: NamedPathRequest,
      repositoryRoot: Path
  ): GeneratedArtifacts = {
    val useCasePackage = GeneratorSupport.useCasePackage(request.modulePath)
    val handlerPackage = GeneratorSupport.handlerPackage(request.modulePath)
    val useCaseName = Naming.typeNameWithSuffix(request.name, "UseCase")
    val handlerName = Naming.typeNameWithSuffix(request.name, "Handler")
    val rendererName = Naming.typeNameWithSuffix(request.name, "Renderer")
    val controllerName = Naming.typeNameWithSuffix(request.name, "Controller")
    val actionName = Naming.toLowerCamel(request.name)
    val handlerField = Naming.toLowerCamel(handlerName)
    val rendererField = Naming.toLowerCamel(rendererName)
    val controllerField = Naming.toLowerCamel(controllerName)

    GeneratedArtifacts(
      files = Seq(
        GeneratedFile(
          repositoryRoot
            .resolve("server/src/main/scala/io/github/stoneream/dachshund/controller")
            .resolve(s"$controllerName.scala")
            .normalize,
          controller(controllerName, handlerPackage, handlerName, handlerField, actionName)
        ),
        GeneratedFile(
          GeneratorSupport.sourcePath(
            repositoryRoot = repositoryRoot,
            sourceRoot = "server/src/main/scala",
            basePackagePath = "io/github/stoneream/dachshund/handler",
            modulePath = request.modulePath,
            fileName = s"$handlerName.scala"
          ),
          handler(handlerPackage, useCasePackage, useCaseName, handlerName, rendererName, rendererField)
        ),
        GeneratedFile(
          GeneratorSupport.sourcePath(
            repositoryRoot = repositoryRoot,
            sourceRoot = "server/src/main/scala",
            basePackagePath = "io/github/stoneream/dachshund/handler",
            modulePath = request.modulePath,
            fileName = s"$rendererName.scala"
          ),
          renderer(handlerPackage, useCasePackage, useCaseName, rendererName)
        )
      ),
      snippets = Seq(
        GeneratedSnippet(
          title = "Root route",
          content = rootSnippet(request.modulePath, controllerName, controllerField, actionName)
        )
      )
    )
  }

  // language=scala 3
  private def controller(
      controllerName: String,
      handlerPackage: String,
      handlerName: String,
      handlerField: String,
      actionName: String
  ): String =
    s"""package io.github.stoneream.dachshund.controller
       |
       |import io.github.stoneream.dachshund.action.TraceAction
       |import io.github.stoneream.dachshund.action.TraceAction.TraceRequest
       |import io.github.stoneream.dachshund.controller.lib.ControllerBaseImpl
       |import $handlerPackage.$handlerName
       |import play.api.mvc.*
       |
       |import com.google.inject.{Inject, Singleton}
       |import scala.concurrent.ExecutionContext
       |
       |@Singleton
       |class $controllerName @Inject() (
       |    cc: ControllerComponents,
       |    traceAction: TraceAction,
       |    $handlerField: $handlerName
       |) extends AbstractController(cc)
       |    with ControllerBaseImpl {
       |  private given ExecutionContext = cc.executionContext
       |
       |  def $actionName(): Action[AnyContent] = traceAction.async { implicit request: TraceRequest[AnyContent] =>
       |    handle($handlerField)(request)
       |  }
       |}
       |""".stripMargin

  // language=scala 3
  private def handler(
      handlerPackage: String,
      useCasePackage: String,
      useCaseName: String,
      handlerName: String,
      rendererName: String,
      rendererField: String
  ): String =
    // language=scala 3
    s"""package $handlerPackage
       |
       |import io.github.stoneream.dachshund.action.TraceAction.TraceRequest
       |import io.github.stoneream.dachshund.handler.lib.{HandlerBase, HtmlRendererBase}
       |import $useCasePackage.{$useCaseName as UseCase, ${useCaseName}Exception as UseCaseException, ${useCaseName}Input as UseCaseInput, ${useCaseName}Output as UseCaseOutput}
       |import play.api.mvc.{AnyContent, Result}
       |
       |import com.google.inject.{Inject, Singleton}
       |import scala.concurrent.Future
       |
       |@Singleton
       |class $handlerName @Inject() (
       |    override val useCase: UseCase,
       |    $rendererField: $rendererName
       |) extends HandlerBase[
       |      TraceRequest[AnyContent],
       |      UseCaseInput,
       |      UseCaseOutput,
       |      UseCaseException,
       |      Result
       |    ] {
       |
       |  def handle(_request: TraceRequest[AnyContent]): Future[UseCaseInput] =
       |    Future.successful(UseCaseInput())
       |
       |  override def renderer: HtmlRendererBase[UseCaseOutput, UseCaseException, Result] =
       |    $rendererField
       |}
       |""".stripMargin

  private def renderer(
      handlerPackage: String,
      useCasePackage: String,
      useCaseName: String,
      rendererName: String
  ): String =
    // language=scala 3
    s"""package $handlerPackage
       |
       |import io.github.stoneream.dachshund.handler.lib.HtmlRendererBase
       |import $useCasePackage.{${useCaseName}Exception as UseCaseException, ${useCaseName}Output as UseCaseOutput}
       |import play.api.mvc.{Result, Results}
       |
       |import com.google.inject.Singleton
       |
       |@Singleton
       |class $rendererName extends HtmlRendererBase[UseCaseOutput, UseCaseException, Result] {
       |  override def success(output: UseCaseOutput): Result = {
       |    val _ = output
       |    Results.Ok("")
       |  }
       |
       |  override def failure(exception: UseCaseException): Result =
       |    Results.InternalServerError(Option(exception.getMessage).getOrElse("Internal server error"))
       |}
       |""".stripMargin

  private def rootSnippet(
      modulePath: String,
      controllerName: String,
      controllerField: String,
      actionName: String
  ): String =
    // language=scala 3
    s"""// Add controller import or constructor dependency:
       |// import io.github.stoneream.dachshund.controller.$controllerName
       |// $controllerField: $controllerName
       |
       |case GET(p"/$modulePath") =>
       |  $controllerField.$actionName()
       |""".stripMargin

}
