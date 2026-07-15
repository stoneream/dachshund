package io.github.stoneream.dachshund.infra.db.generated

import java.time.LocalDateTime

final case class ExternalAuthRequestDbRow(
    id: Long,
    flowType: String,
    providerType: String,
    state: String,
    nonce: String,
    codeVerifier: Option[String],
    redirectUri: String,
    scopes: String,
    status: String,
    expiresAt: LocalDateTime,
    completedAt: Option[LocalDateTime],
    errorCode: Option[String],
    errorDescription: Option[String],
    createdAt: LocalDateTime,
    updatedAt: LocalDateTime,
    deletedAt: Option[LocalDateTime],
    createdUser: String,
    updatedUser: String,
    deletedUser: String,
    deleted: Long,
    lockVersion: Long
)
