package io.github.stoneream.dachshund.codegen

import io.github.stoneream.dachshund.codegen.generator.{GenerateDaemon, GenerateDbAccessor, GenerateDbMetadata, GenerateEndpoint, GenerateStep, GenerateUseCase}
import io.github.stoneream.dachshund.codegen.model.GeneratedArtifactWriter
import scopt.OParser

import java.nio.file.Path

object DachshundCodeGenMain {
  def main(args: Array[String]): Unit = {
    val builder = OParser.builder[CommandArgs]
    val parser = {
      import builder.*
      OParser.sequence(
        programName("dachshund-codegen"),
        head("dachshund-codegen"),
        opt[String]("usecase")
          .optional()
          .action((value, config) => config.copy(usecase = Some(value)))
          .text("UseCase を生成する module path。例: spotify/auth/signup"),
        opt[String]("step")
          .optional()
          .action((value, config) => config.copy(step = Some(value)))
          .text("step を生成する usecase module path。例: spotify/auth/signup"),
        opt[String]("endpoint")
          .optional()
          .action((value, config) => config.copy(endpoint = Some(value)))
          .text("server endpoint を生成する module path。例: spotify/auth/signup"),
        opt[String]("daemon")
          .optional()
          .action((value, config) => config.copy(daemon = Some(value)))
          .text("daemon job を生成する module path。例: spotify/access-token-refresh"),
        opt[String]("daemon-handler")
          .optional()
          .action((value, config) => config.copy(daemonHandler = Some(value)))
          .text("daemon handler を生成する module path。例: spotify/access-token-refresh"),
        opt[Unit]("db-metadata")
          .optional()
          .action((_, config) => config.copy(dbMetadata = true))
          .text("tbls schema JSON から DB table metadata を生成する"),
        opt[String]("db-accessor")
          .optional()
          .action((value, config) => config.copy(dbAccessor = Some(value)))
          .text("DB reader/writer skeleton を生成する module path。例: spotify/auth"),
        opt[String]("name")
          .optional()
          .action((value, config) => config.copy(name = Some(value)))
          .text("生成する型名のベース。例: SpotifyAuthSignup"),
        opt[String]("table")
          .optional()
          .action((value, config) => config.copy(table = Some(value)))
          .text("DB metadata 生成対象の table 名。例: spotify_authorizations"),
        opt[String]("reader")
          .optional()
          .action((value, config) => config.copy(reader = Some(value)))
          .text("DB reader class 名。例: SpotifyAuthorizationReader"),
        opt[String]("writer")
          .optional()
          .action((value, config) => config.copy(writer = Some(value)))
          .text("DB writer class 名。例: SpotifyAuthorizationWriter"),
        opt[String]("schema-json")
          .optional()
          .action((value, config) => config.copy(schemaJson = Path.of(value)))
          .text("tbls schema JSON のパス。デフォルト: tbls/schema/schema.json"),
        opt[String]("repository-root")
          .optional()
          .action((value, config) => config.copy(repositoryRoot = Path.of(value)))
          .text("repository root のパス。デフォルト: カレントディレクトリ"),
        opt[Unit]("force")
          .optional()
          .action((_, config) => config.copy(force = true))
          .text("既存ファイルを上書きする"),
        opt[Unit]("dry-run")
          .optional()
          .action((_, config) => config.copy(dryRun = true))
          .text("ファイルを書き込まず、生成予定ファイルと snippet だけ表示する")
      )
    }

    OParser.parse(parser, args, CommandArgs()) match {
      case Some(config) =>
        config.resolve match {
          case Right(request) =>
            val artifacts = render(request, config.repositoryRoot.normalize)
            GeneratedArtifactWriter.write(artifacts, config.force, config.dryRun)
          case Left(message) =>
            System.err.println(message)
            sys.exit(1)
        }
      case None =>
        sys.exit(1)
    }
  }

  private def render(
      request: GenerationRequest,
      repositoryRoot: Path
  ) =
    request match {
      case GenerationRequest.UseCase(namedPathRequest) =>
        GenerateUseCase.render(namedPathRequest, repositoryRoot)
      case GenerationRequest.Step(namedPathRequest) =>
        GenerateStep.render(namedPathRequest, repositoryRoot)
      case GenerationRequest.Endpoint(namedPathRequest) =>
        GenerateEndpoint.render(namedPathRequest, repositoryRoot)
      case GenerationRequest.Daemon(namedPathRequest) =>
        GenerateDaemon.render(namedPathRequest, repositoryRoot)
      case GenerationRequest.DaemonHandler(namedPathRequest) =>
        GenerateDaemon.renderHandler(namedPathRequest, repositoryRoot)
      case GenerationRequest.DbMetadata(table, schemaJson) =>
        GenerateDbMetadata.render(table, schemaJson, repositoryRoot)
      case GenerationRequest.DbAccessor(modulePath, reader, writer) =>
        GenerateDbAccessor.render(modulePath, reader, writer, repositoryRoot)
    }
}
