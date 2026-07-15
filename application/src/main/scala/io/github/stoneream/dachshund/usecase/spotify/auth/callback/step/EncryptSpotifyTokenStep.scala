package io.github.stoneream.dachshund.usecase.spotify.auth.callback.step

import io.github.stoneream.dachshund.config.ApplicationConfig
import io.github.stoneream.dachshund.lib.encrypt.spotify.{SpotifyTokenEncryptionAad, SpotifyTokenEncryptionException, SpotifyTokenEncryptor}
import io.github.stoneream.dachshund.lib.executor.Executors.DefaultExecutor
import io.github.stoneream.dachshund.service.spotify.oauth_client.SpotifyOAuthClient.TokenResponse as SpotifyTokenResponse
import io.github.stoneream.dachshund.usecase.spotify.auth.callback.SpotifyAuthCallbackUseCaseException as UseCaseException
import io.github.stoneream.dachshund.usecase.spotify.auth.callback.context.EncryptedSpotifyTokenPair

import com.google.inject.{Inject, Singleton}
import scala.concurrent.Future
import scala.util.control.NonFatal

/**
 * Spotifyトークンをユーザーに紐づくAADで暗号化
 */
@Singleton
private[callback] class EncryptSpotifyTokenStep @Inject() (
    applicationConfig: ApplicationConfig,
    spotifyTokenEncryptor: SpotifyTokenEncryptor,
    defaultExecutor: DefaultExecutor
) {
  def run(
      userId: Long,
      tokenResponse: SpotifyTokenResponse
  ): Future[EncryptedSpotifyTokenPair] =
    Future {
      val refreshToken = tokenResponse.refreshToken
        .map(_.trim)
        .filter(_.nonEmpty)
        .getOrElse {
          throw UseCaseException.RefreshTokenMissing
        }
      val keyVersion = applicationConfig.spotify.token.encryptionKeyVersion

      EncryptedSpotifyTokenPair(
        accessToken = spotifyTokenEncryptor.encrypt(
          tokenResponse.accessToken,
          Some(SpotifyTokenEncryptionAad.accessToken(userId, keyVersion))
        ),
        refreshToken = spotifyTokenEncryptor.encrypt(
          refreshToken,
          Some(SpotifyTokenEncryptionAad.refreshToken(userId, keyVersion))
        )
      )
    }(using defaultExecutor).recoverWith {
      case exception: UseCaseException =>
        Future.failed(exception)
      case _: SpotifyTokenEncryptionException =>
        Future.failed(UseCaseException.MissingConfiguration("spotify.token.encryption-key"))
      case NonFatal(e) =>
        Future.failed(UseCaseException.TokenExchangeFailed(e))
    }(using defaultExecutor)
}
