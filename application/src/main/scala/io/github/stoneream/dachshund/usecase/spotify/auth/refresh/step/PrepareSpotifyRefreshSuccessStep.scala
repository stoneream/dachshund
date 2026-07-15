package io.github.stoneream.dachshund.usecase.spotify.auth.refresh.step

import io.github.stoneream.dachshund.config.ApplicationConfig
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.encrypt.spotify.{SpotifyTokenEncryptionAad, SpotifyTokenEncryptionException, SpotifyTokenEncryptor}
import io.github.stoneream.dachshund.service.spotify.oauth_client.SpotifyOAuthClient.TokenResponse
import io.github.stoneream.dachshund.usecase.spotify.auth.refresh.context.SpotifyRefreshPreparationResult.{Failed, Prepared}
import io.github.stoneream.dachshund.usecase.spotify.auth.refresh.context.{SpotifyAuthorizationRefreshTarget, SpotifyRefreshFailure, SpotifyRefreshFailureType, SpotifyRefreshPreparationResult, SpotifyRefreshedTokens}

import com.google.inject.{Inject, Singleton}
import scala.concurrent.duration.*
import scala.util.control.NonFatal

/**
 * Spotify refresh 成功レスポンスを検証し、永続化用の token に変換
 */
@Singleton
private[refresh] class PrepareSpotifyRefreshSuccessStep @Inject() (
    applicationConfig: ApplicationConfig,
    spotifyTokenEncryptor: SpotifyTokenEncryptor
) {
  def run(
      target: SpotifyAuthorizationRefreshTarget,
      tokenResponse: TokenResponse,
      currentRefreshToken: String,
      now: BusinessDateTime
  ): SpotifyRefreshPreparationResult =
    if (isInsufficientScope(target.scopeText, tokenResponse.scope)) {
      Failed(SpotifyRefreshFailure(SpotifyRefreshFailureType.InsufficientScope))
    } else {
      prepareRefreshedTokens(target, tokenResponse, currentRefreshToken, now)
    }

  private def prepareRefreshedTokens(
      target: SpotifyAuthorizationRefreshTarget,
      tokenResponse: TokenResponse,
      currentRefreshToken: String,
      now: BusinessDateTime
  ): SpotifyRefreshPreparationResult =
    try {
      val keyVersion = applicationConfig.spotify.token.encryptionKeyVersion
      val refreshToken = tokenResponse.refreshToken.map(_.trim).filter(_.nonEmpty).getOrElse(currentRefreshToken)
      val accessTokenExpiresAt = now.plus(tokenResponse.expiresIn.seconds)
      val refreshedTokens = SpotifyRefreshedTokens(
        encryptedAccessToken = spotifyTokenEncryptor.encrypt(
          tokenResponse.accessToken,
          Some(SpotifyTokenEncryptionAad.accessToken(target.userId, keyVersion))
        ),
        encryptedRefreshToken = spotifyTokenEncryptor.encrypt(
          refreshToken,
          Some(SpotifyTokenEncryptionAad.refreshToken(target.userId, keyVersion))
        ),
        tokenType = Option(tokenResponse.tokenType).map(_.trim).filter(_.nonEmpty).getOrElse(target.tokenType),
        scopeText = normalizeScopeText(tokenResponse.scope.getOrElse(target.scopeText)),
        accessTokenExpiresAt = accessTokenExpiresAt,
        nextRefreshAttemptAt = accessTokenExpiresAt.minus(target.refreshMarginSeconds.seconds)
      )

      Prepared(refreshedTokens)
    } catch {
      case _: SpotifyTokenEncryptionException =>
        Failed(SpotifyRefreshFailure(SpotifyRefreshFailureType.Unknown))
      case NonFatal(_) =>
        Failed(SpotifyRefreshFailure(SpotifyRefreshFailureType.Unknown))
    }

  private def isInsufficientScope(targetScopeText: String, refreshedScope: Option[String]): Boolean =
    refreshedScope.exists { scopeText =>
      val targetScopes = normalizeScopeText(targetScopeText).split(" ").filter(_.nonEmpty).toSet
      val refreshedScopes = normalizeScopeText(scopeText).split(" ").filter(_.nonEmpty).toSet
      !targetScopes.subsetOf(refreshedScopes)
    }

  private def normalizeScopeText(scopeText: String): String =
    scopeText
      .split("\\s+")
      .map(_.trim)
      .filter(_.nonEmpty)
      .distinct
      .sorted
      .mkString(" ")
}
