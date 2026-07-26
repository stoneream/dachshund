package io.github.stoneream.dachshund.codegen.generator

import io.github.stoneream.dachshund.codegen.NamedPathRequest
import io.github.stoneream.dachshund.codegen.schema.{TblsColumn, TblsTable}
import org.scalatest.OptionValues
import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.Path

class GeneratorSpec extends AnyFeatureSpec with Matchers with OptionValues {
  private val repositoryRoot = Path.of("/repo")

  Feature("codegen generator") {
    Scenario("usecase ファイルを application module に生成する") {
      val artifacts = GenerateUseCase.render(
        request = NamedPathRequest("spotify/auth/signup", "SpotifyAuthSignup"),
        repositoryRoot = repositoryRoot
      )

      artifacts.files.map(_.path.toString) should contain allOf (
        "/repo/application/src/main/scala/io/github/stoneream/dachshund/usecase/spotify/auth/signup/SpotifyAuthSignupUseCase.scala",
        "/repo/application/src/main/scala/io/github/stoneream/dachshund/usecase/spotify/auth/signup/SpotifyAuthSignupUseCaseInput.scala",
        "/repo/application/src/main/scala/io/github/stoneream/dachshund/usecase/spotify/auth/signup/SpotifyAuthSignupUseCaseOutput.scala",
        "/repo/application/src/main/scala/io/github/stoneream/dachshund/usecase/spotify/auth/signup/SpotifyAuthSignupUseCaseException.scala"
      )
      artifacts.files.find(_.path.toString.endsWith("SpotifyAuthSignupUseCase.scala")).map(_.content).value should include("extends UseCase")
    }

    Scenario("step ファイルを usecase step package に生成する") {
      val artifacts = GenerateStep.render(
        request = NamedPathRequest("spotify/auth/signup", "BuildAuthorizationRequest"),
        repositoryRoot = repositoryRoot
      )

      artifacts.files.map(_.path.toString) should contain(
        "/repo/application/src/main/scala/io/github/stoneream/dachshund/usecase/spotify/auth/signup/step/BuildAuthorizationRequestStep.scala"
      )
      artifacts.files.head.content should include("private[signup] class BuildAuthorizationRequestStep")
    }

    Scenario("endpoint ファイルと root snippet を生成する") {
      val artifacts = GenerateEndpoint.render(
        request = NamedPathRequest("spotify/auth/signup", "SpotifyAuthSignup"),
        repositoryRoot = repositoryRoot
      )

      artifacts.files.map(_.path.toString) should contain allOf (
        "/repo/server/src/main/scala/io/github/stoneream/dachshund/controller/SpotifyAuthSignupController.scala",
        "/repo/server/src/main/scala/io/github/stoneream/dachshund/handler/spotify/auth/signup/SpotifyAuthSignupHandler.scala",
        "/repo/server/src/main/scala/io/github/stoneream/dachshund/handler/spotify/auth/signup/SpotifyAuthSignupRenderer.scala"
      )
      artifacts.snippets.map(_.content).mkString("\n") should include("""case GET(p"/spotify/auth/signup")""")
    }

    Scenario("daemon handler と definition と snippets を生成する") {
      val artifacts = GenerateDaemon.render(
        request = NamedPathRequest("spotify/access-token-refresh", "SpotifyAccessTokenRefresh"),
        repositoryRoot = repositoryRoot
      )

      artifacts.files.map(_.path.toString) should contain allOf (
        "/repo/daemon/src/main/scala/io/github/stoneream/dachshund/daemon/handler/spotify/access_token_refresh/SpotifyAccessTokenRefreshHandler.scala",
        "/repo/daemon/src/main/scala/io/github/stoneream/dachshund/daemon/config/SpotifyAccessTokenRefreshJobConfig.scala",
        "/repo/daemon/src/main/scala/io/github/stoneream/dachshund/daemon/handler/spotify/access_token_refresh/SpotifyAccessTokenRefreshJob.scala"
      )
      artifacts.files.map(_.content).mkString("\n") should include("extends JobHandler")
      artifacts.files.map(_.content).mkString("\n") should include("config: SpotifyAccessTokenRefreshJobConfig")
      artifacts.files.map(_.content).mkString("\n") should include("extends JobConfig")
      artifacts.files.map(_.content).mkString("\n") should include("JobSettingConfig(")
      val configContent = artifacts.files.find(_.path.toString.endsWith("JobConfig.scala")).map(_.content).value
      configContent should include("""private val Name: String = "spotify-access-token-refresh"""")
      configContent should include("enabled: Boolean")
      configContent should include("enabled = enabled")
      configContent should include("retry: JobRetryPolicy")
      configContent should include("retry = retry")
      configContent should not include "JobName("
      val snippetContent = artifacts.snippets.map(_.content).mkString("\n")
      snippetContent should include("spotify-access-token-refresh")
      snippetContent should include("JobLoaderImpl constructor")
      snippetContent should include("JobLoaderImpl.load List")
      snippetContent should include("spotifyAccessTokenRefreshJob")
      snippetContent should not include "spotifyAccessTokenRefreshJob.job"
      artifacts.files.map(_.content).mkString("\n") should include("extends Job")
      artifacts.files.map(_.content).mkString("\n") should include("override def dispatch()")
      artifacts.files.map(_.content).mkString("\n") should include("@Inject")
      artifacts.files.map(_.content).mkString("\n") should include("@Singleton")
      artifacts.files.map(_.content).mkString("\n") should not include "val layer: ZLayer"
      snippetContent should include("DaemonModule")
      snippetContent should not include "DaemonLayers"
      snippetContent should not include "SpotifyAccessTokenRefreshHandler.layer"
      snippetContent should not include "SpotifyAccessTokenRefreshJob.layer"
      snippetContent should not include "bind(classOf"
      snippetContent should include("daemon/src/main/resources/application.conf")
      snippetContent should include("enabled = true")
      snippetContent should include("enabled = ${?DAEMON_JOB_SPOTIFY_ACCESS_TOKEN_REFRESH_ENABLED}")
      snippetContent should include("interval = 1m")
      snippetContent should include("timeout = 5m")
      snippetContent should include("max-attempts = 3")
      snippetContent should include("base-delay = 1s")
      snippetContent should include("max-delay = 30s")
      snippetContent should include("jitter-ratio = 0.2")
      snippetContent should not include "batch-size"
    }

    Scenario("daemon handler だけを生成する") {
      val artifacts = GenerateDaemon.renderHandler(
        request = NamedPathRequest("spotify/access-token-refresh", "SpotifyAccessTokenRefresh"),
        repositoryRoot = repositoryRoot
      )

      artifacts.files.map(_.path.toString) should contain only (
        "/repo/daemon/src/main/scala/io/github/stoneream/dachshund/daemon/handler/spotify/access_token_refresh/SpotifyAccessTokenRefreshHandler.scala"
      )
      val handlerContent = artifacts.files.head.content
      handlerContent should include("import io.github.stoneream.dachshund.daemon.job.JobHandler")
      handlerContent should not include "DaemonJobResult"
      handlerContent should include("extends JobHandler")
      handlerContent should include("Task[Unit]")
      handlerContent should include(".unit")
      artifacts.snippets shouldBe empty
    }

    Scenario("table schema から db metadata を生成する") {
      val artifacts = GenerateDbMetadata.renderTable(
        table = TblsTable(
          name = "external_auth_request",
          columns = Seq(
            TblsColumn("id", "bigint unsigned", nullable = false),
            TblsColumn("state", "varchar(255)", nullable = false),
            TblsColumn("completed_at", "datetime", nullable = true),
            TblsColumn("created_at", "datetime", nullable = false),
            TblsColumn("created_user", "varchar(255)", nullable = false)
          )
        ),
        repositoryRoot = repositoryRoot
      )

      artifacts.files.map(_.path.toString) should contain allOf (
        "/repo/application/src/main/scala/io/github/stoneream/dachshund/infra/db/generated/ExternalAuthRequestDbRow.scala",
        "/repo/application/src/main/scala/io/github/stoneream/dachshund/infra/db/generated/ExternalAuthRequestTable.scala"
      )
      val rowContent = artifacts.files.find(_.path.toString.endsWith("ExternalAuthRequestDbRow.scala")).map(_.content).value
      val tableContent = artifacts.files.find(_.path.toString.endsWith("ExternalAuthRequestTable.scala")).map(_.content).value
      rowContent should include("final case class ExternalAuthRequestDbRow")
      rowContent should include("completedAt: Option[LocalDateTime]")
      tableContent should include(""""external_auth_request"""")
      tableContent should include("rs.localDateTimeOpt(Columns.CompletedAt)")
      tableContent should include("InsertAuditColumnNames: Seq[String] = Seq(Columns.CreatedAt, Columns.CreatedUser)")
    }

    Scenario("db accessor skeleton には repository や広い永続化メソッドを生成しない") {
      val artifacts = GenerateDbAccessor.render(
        modulePath = "spotify/auth",
        readerName = "SpotifyAuthorizationReader",
        writerName = "SpotifyAuthorizationWriter",
        repositoryRoot = repositoryRoot
      )
      val content = artifacts.files.map(_.content).mkString("\n")

      content should not include "trait"
      content should not include "Repository"
      content should not include "findAll"
      content should not include "save"
      content should not include "upsert"
    }
  }
}
