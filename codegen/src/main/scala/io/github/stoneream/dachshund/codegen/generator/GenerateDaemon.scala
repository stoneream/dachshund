package io.github.stoneream.dachshund.codegen.generator

import io.github.stoneream.dachshund.codegen.NamedPathRequest
import io.github.stoneream.dachshund.codegen.model.{GeneratedArtifacts, GeneratedFile, GeneratedSnippet}
import io.github.stoneream.dachshund.codegen.util.Naming

import java.nio.file.Path

object GenerateDaemon {
  def renderHandler(
      request: NamedPathRequest,
      repositoryRoot: Path
  ): GeneratedArtifacts =
    GeneratedArtifacts(
      files = Seq(handlerFile(request, repositoryRoot))
    )

  def render(
      request: NamedPathRequest,
      repositoryRoot: Path
  ): GeneratedArtifacts = {
    val packageName = GeneratorSupport.daemonHandlerPackage(request.modulePath)
    val handlerName = Naming.typeNameWithSuffix(request.name, "Handler")
    val jobName = Naming.typeNameWithSuffix(request.name, "Job")
    val configName = Naming.typeNameWithSuffix(request.name, "JobConfig")
    val daemonName = request.modulePath.split("/").mkString("-")
    val jobParameterName = Naming.toLowerCamel(jobName)
    val configParameterName = Naming.toLowerCamel(request.name)
    GeneratedArtifacts(
      files = Seq(
        handlerFile(request, repositoryRoot),
        configFile(request, repositoryRoot),
        GeneratedFile(
          GeneratorSupport.sourcePath(
            repositoryRoot = repositoryRoot,
            sourceRoot = "daemon/src/main/scala",
            basePackagePath = "io/github/stoneream/dachshund/daemon/handler",
            modulePath = request.modulePath,
            fileName = s"$jobName.scala"
          ),
          job(packageName, handlerName, jobName, configName)
        )
      ),
      snippets = Seq(
        GeneratedSnippet(
          title = "Daemon job loader",
          content = loaderSnippet(packageName, jobName, configName, daemonName, jobParameterName, configParameterName)
        )
      )
    )
  }

  private def handlerFile(
      request: NamedPathRequest,
      repositoryRoot: Path
  ): GeneratedFile = {
    val packageName = GeneratorSupport.daemonHandlerPackage(request.modulePath)
    val handlerName = Naming.typeNameWithSuffix(request.name, "Handler")

    GeneratedFile(
      GeneratorSupport.sourcePath(
        repositoryRoot = repositoryRoot,
        sourceRoot = "daemon/src/main/scala",
        basePackagePath = "io/github/stoneream/dachshund/daemon/handler",
        modulePath = request.modulePath,
        fileName = s"$handlerName.scala"
      ),
      handler(packageName, handlerName, request)
    )
  }

  private def configFile(
      request: NamedPathRequest,
      repositoryRoot: Path
  ): GeneratedFile = {
    val configName = Naming.typeNameWithSuffix(request.name, "JobConfig")
    val daemonName = request.modulePath.split("/").mkString("-")

    GeneratedFile(
      GeneratorSupport.sourcePath(
        repositoryRoot = repositoryRoot,
        sourceRoot = "daemon/src/main/scala",
        basePackagePath = "io/github/stoneream/dachshund/daemon/config",
        modulePath = "",
        fileName = s"$configName.scala"
      ),
      config(configName, daemonName)
    )
  }

  private def handler(
      packageName: String,
      handlerName: String,
      request: NamedPathRequest
  ): String = {
    val useCasePackage = GeneratorSupport.useCasePackage(request.modulePath)
    val useCaseName = Naming.typeNameWithSuffix(request.name, "UseCase")
    val inputName = s"${useCaseName}Input"

    // language=scala 3
    s"""package $packageName
       |
       |import com.google.inject.{Inject, Singleton}
       |import io.github.stoneream.dachshund.daemon.job.JobHandler
       |import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
       |import $useCasePackage.{$inputName as UseCaseInput, $useCaseName as UseCase}
       |import zio.{Task, ZIO}
       |
       |@Singleton
       |class $handlerName @Inject() (
       |    useCase: UseCase
       |) extends JobHandler {
       |  override def handle()(using LoggingContext): Task[Unit] =
       |    ZIO.fromFuture(_ => useCase.run(UseCaseInput())).unit
       |}
       |""".stripMargin
  }

  private def job(
      packageName: String,
      handlerName: String,
      jobName: String,
      configName: String
  ): String = {
    // language=scala 3
    s"""package $packageName
       |
       |import com.google.inject.{Inject, Singleton}
       |import io.github.stoneream.dachshund.daemon.config.$configName
       |import io.github.stoneream.dachshund.daemon.job.model.Job
       |import io.github.stoneream.dachshund.logging.TraceLogger.LoggingContext
       |import zio.Task
       |
       |@Singleton
       |class $jobName @Inject() (
       |    handler: $handlerName,
       |    config: $configName
       |) extends Job {
       |  override val setting = config.setting
       |
       |  override def dispatch()(using LoggingContext): Task[Unit] =
       |    handler.handle()
       |}
       |""".stripMargin
  }

  private def config(
      configName: String,
      daemonName: String
  ): String = {
    val rawConfigName = s"Raw$configName"

    // language=scala 3
    s"""package io.github.stoneream.dachshund.daemon.config
       |
       |import pureconfig.ConfigReader
       |import pureconfig.error.CannotConvert
       |
       |import scala.concurrent.duration.FiniteDuration
       |
       |final case class $configName(
       |    override val setting: JobSetting
       |) extends JobConfig
       |
       |object $configName {
       |  private val Name: String = "$daemonName"
       |  private val ConfigPath: String = "daemon.jobs.$daemonName"
       |
       |  private final case class $rawConfigName(
       |      interval: FiniteDuration,
       |      timeout: FiniteDuration,
       |      retry: JobRetryPolicy
       |  ) derives ConfigReader {
       |    def settingConfig: JobSettingConfig =
       |      JobSettingConfig(
       |        interval = interval,
       |        timeout = timeout,
       |        retry = retry
       |      )
       |  }
       |
       |  given ConfigReader[$configName] =
       |    summon[ConfigReader[$rawConfigName]].emap(validate)
       |
       |  private def validate(raw: $rawConfigName): Either[CannotConvert, $configName] =
       |    raw.settingConfig.toJobSetting(Name, ConfigPath).map { setting =>
       |      $configName(setting = setting)
       |    }
       |}
       |""".stripMargin
  }

  private def loaderSnippet(
      packageName: String,
      jobName: String,
      configName: String,
      daemonName: String,
      jobParameterName: String,
      configParameterName: String
  ): String = {
    // language=scala 3
    s"""// Add to JobLoaderImpl constructor:
       |// import $packageName.$jobName
       |// $jobParameterName: $jobName
       |//
       |// Add to JobLoaderImpl.load List:
       |// $jobParameterName
       |
       |// Add to DaemonModule:
       |// @Provides
       |// @Singleton
       |// def provide${configName}(daemonConfig: DaemonConfig): $configName =
       |//   daemonConfig.jobs.$configParameterName
       |//
       |// Add to DaemonJobsConfig:
       |// $configParameterName: $configName
       |//
       |// Add to daemon/src/main/resources/application.conf:
       |// daemon.jobs.$daemonName {
       |//   interval = 1m
       |//   timeout = 5m
       |//   retry {
       |//     max-attempts = 3
       |//     base-delay = 1s
       |//     max-delay = 30s
       |//     jitter-ratio = 0.2
       |//   }
       |// }
       |
       |// $daemonName is loaded when JobLoaderImpl returns $jobParameterName.
       |""".stripMargin
  }
}
