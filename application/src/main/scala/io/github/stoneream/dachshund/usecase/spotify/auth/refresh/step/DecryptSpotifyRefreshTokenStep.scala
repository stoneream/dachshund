package io.github.stoneream.dachshund.usecase.spotify.auth.refresh.step

import io.github.stoneream.dachshund.lib.encrypt.spotify.{SpotifyTokenEncryptionAad, SpotifyTokenEncryptionException, SpotifyTokenEncryptor}
import io.github.stoneream.dachshund.usecase.spotify.auth.refresh.context.{SpotifyAuthorizationRefreshTarget, SpotifyRefreshFailure, SpotifyRefreshFailureType}

import com.google.inject.{Inject, Singleton}
import scala.util.control.NonFatal

/**
 * 暗号化済み Spotify refresh token を復号
 */
@Singleton
private[refresh] class DecryptSpotifyRefreshTokenStep @Inject() (
    spotifyTokenEncryptor: SpotifyTokenEncryptor
) {
  def run(target: SpotifyAuthorizationRefreshTarget): Either[SpotifyRefreshFailure, String] =
    try {
      Right(
        spotifyTokenEncryptor.decrypt(
          target.encryptedRefreshToken,
          Some(SpotifyTokenEncryptionAad.refreshToken(target.userId, target.encryptedRefreshToken.keyVersion))
        )
      )
    } catch {
      case _: SpotifyTokenEncryptionException =>
        Left(SpotifyRefreshFailure(SpotifyRefreshFailureType.TokenDecryptFailed))
      case NonFatal(_) =>
        Left(SpotifyRefreshFailure(SpotifyRefreshFailureType.TokenDecryptFailed))
    }
}
