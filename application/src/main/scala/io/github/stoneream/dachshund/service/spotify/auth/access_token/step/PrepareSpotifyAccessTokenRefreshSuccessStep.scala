package io.github.stoneream.dachshund.service.spotify.auth.access_token.step

import io.github.stoneream.dachshund.config.ApplicationConfig
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.lib.encrypt.spotify.{SpotifyTokenEncryptionAad, SpotifyTokenEncryptionException, SpotifyTokenEncryptor}
import io.github.stoneream.dachshund.service.spotify.auth.access_token.context.{SpotifyAccessTokenRefreshFailure, SpotifyAccessTokenRefreshFailureReason, SpotifyAccessTokenRefreshedTokens}
import io.github.stoneream.dachshund.service.spotify.auth.access_token.model.SpotifyAccessTokenResolveTarget
import io.github.stoneream.dachshund.service.spotify.oauth_client.SpotifyOAuthClient.TokenResponse

import com.google.inject.{Inject, Singleton}
import scala.concurrent.duration.*
import scala.util.control.NonFatal

/** 更新成功後の Spotify アクセストークン情報を検証し、保存用に暗号化する。 */
@Singleton
class PrepareSpotifyAccessTokenRefreshSuccessStep @Inject() (
    applicationConfig: ApplicationConfig,
    spotifyTokenEncryptor: SpotifyTokenEncryptor
) {
  def run(
      target: SpotifyAccessTokenResolveTarget,
      tokenResponse: TokenResponse,
      currentRefreshToken: String,
      now: BusinessDateTime
  ): Either[SpotifyAccessTokenRefreshFailure, SpotifyAccessTokenRefreshedTokens] =
    if (isInsufficientScope(target.scopeText, tokenResponse.scope)) {
      Left(SpotifyAccessTokenRefreshFailure(SpotifyAccessTokenRefreshFailureReason.InsufficientScope))
    } else {
      prepareRefreshedTokens(target, tokenResponse, currentRefreshToken, now)
    }

  private def prepareRefreshedTokens(
      target: SpotifyAccessTokenResolveTarget,
      tokenResponse: TokenResponse,
      currentRefreshToken: String,
      now: BusinessDateTime
  ): Either[SpotifyAccessTokenRefreshFailure, SpotifyAccessTokenRefreshedTokens] =
    try {
      val keyVersion = applicationConfig.spotify.token.encryptionKeyVersion
      val refreshToken = tokenResponse.refreshToken.map(_.trim).filter(_.nonEmpty).getOrElse(currentRefreshToken)
      val tokenType = Option(tokenResponse.tokenType).map(_.trim).filter(_.nonEmpty).getOrElse(target.tokenType)
      val scopeText = NormalizeSpotifyScopeText(tokenResponse.scope.getOrElse(target.scopeText))
      val accessTokenExpiresAt = now.plus(tokenResponse.expiresIn.seconds)

      Right(
        SpotifyAccessTokenRefreshedTokens(
          accessToken = tokenResponse.accessToken,
          encryptedAccessToken = spotifyTokenEncryptor.encrypt(
            tokenResponse.accessToken,
            Some(SpotifyTokenEncryptionAad.accessToken(target.userId, keyVersion))
          ),
          encryptedRefreshToken = spotifyTokenEncryptor.encrypt(
            refreshToken,
            Some(SpotifyTokenEncryptionAad.refreshToken(target.userId, keyVersion))
          ),
          tokenType = tokenType,
          scopeText = scopeText,
          accessTokenExpiresAt = accessTokenExpiresAt,
          nextRefreshAttemptAt = accessTokenExpiresAt.minus(target.refreshMarginSeconds.seconds)
        )
      )
    } catch {
      case _: SpotifyTokenEncryptionException =>
        Left(SpotifyAccessTokenRefreshFailure(SpotifyAccessTokenRefreshFailureReason.Unknown))
      case NonFatal(_) =>
        Left(SpotifyAccessTokenRefreshFailure(SpotifyAccessTokenRefreshFailureReason.Unknown))
    }

  private def isInsufficientScope(targetScopeText: String, refreshedScope: Option[String]): Boolean =
    refreshedScope.exists { scopeText =>
      val targetScopes = NormalizeSpotifyScopeText(targetScopeText).split(" ").filter(_.nonEmpty).toSet
      val refreshedScopes = NormalizeSpotifyScopeText(scopeText).split(" ").filter(_.nonEmpty).toSet
      !targetScopes.subsetOf(refreshedScopes)
    }
}
