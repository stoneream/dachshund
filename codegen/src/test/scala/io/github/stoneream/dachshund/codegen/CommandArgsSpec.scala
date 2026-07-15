package io.github.stoneream.dachshund.codegen

import org.scalatest.featurespec.AnyFeatureSpec
import org.scalatest.matchers.should.Matchers

import java.nio.file.Path

class CommandArgsSpec extends AnyFeatureSpec with Matchers {
  Feature("codegen のコマンド引数") {
    Scenario("usecase 生成リクエストを解決する") {
      CommandArgs(
        usecase = Some("spotify/auth/signup"),
        name = Some("SpotifyAuthSignup")
      ).resolve shouldBe Right(
        GenerationRequest.UseCase(
          NamedPathRequest(
            modulePath = "spotify/auth/signup",
            name = "SpotifyAuthSignup"
          )
        )
      )
    }

    Scenario("複数の生成対象が指定された場合は拒否する") {
      val result = CommandArgs(
        usecase = Some("spotify/auth/signup"),
        daemonHandler = Some("spotify/access-token-refresh"),
        name = Some("SpotifyAuthSignup")
      ).resolve

      result.isLeft shouldBe true
    }

    Scenario("daemon handler 生成リクエストを解決する") {
      CommandArgs(
        daemonHandler = Some("spotify/access-token-refresh"),
        name = Some("SpotifyAccessTokenRefresh")
      ).resolve shouldBe Right(
        GenerationRequest.DaemonHandler(
          NamedPathRequest(
            modulePath = "spotify/access-token-refresh",
            name = "SpotifyAccessTokenRefresh"
          )
        )
      )
    }

    Scenario("schema path 付きの db metadata 生成リクエストを解決する") {
      CommandArgs(
        dbMetadata = true,
        table = Some("external_auth_request"),
        schemaJson = Path.of("tmp/schema.json"),
        repositoryRoot = Path.of("/repo")
      ).resolve shouldBe Right(
        GenerationRequest.DbMetadata(
          table = "external_auth_request",
          schemaJson = Path.of("/repo/tmp/schema.json")
        )
      )
    }
  }
}
