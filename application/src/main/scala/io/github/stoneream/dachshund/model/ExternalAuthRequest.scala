package io.github.stoneream.dachshund.model

import java.time.LocalDateTime

final case class ExternalAuthRequest(
    id: Long,
    flowType: ExternalAuthFlowType,
    providerType: ExternalAuthProviderType,
    state: String,
    nonce: String,
    codeVerifier: Option[String],
    redirectUri: String,
    scopes: String,
    status: ExternalAuthRequestStatus,
    expiresAt: LocalDateTime,
    completedAt: Option[LocalDateTime],
    errorCode: Option[String],
    errorDescription: Option[String]
)
