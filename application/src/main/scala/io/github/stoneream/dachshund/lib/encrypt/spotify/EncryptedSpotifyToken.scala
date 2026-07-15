package io.github.stoneream.dachshund.lib.encrypt.spotify

final case class EncryptedSpotifyToken(
    cipherText: Array[Byte],
    nonce: Array[Byte],
    tag: Array[Byte],
    algorithm: String,
    keyVersion: String
)
