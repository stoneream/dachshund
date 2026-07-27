package io.github.stoneream.dachshund.infra.db.ex

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.model.{ExternalAuthFlowType, ExternalAuthProviderType, ExternalAuthRequestStatus}

final case class ExternalAuthRequestSource(
    id: Long = 0L,
    flowType: ExternalAuthFlowType,
    providerType: ExternalAuthProviderType,
    state: String,
    nonce: String,
    codeVerifier: Option[String],
    redirectUri: String,
    scopes: String,
    status: ExternalAuthRequestStatus,
    expiresAt: BusinessDateTime,
    completedAt: Option[BusinessDateTime],
    errorCode: Option[String],
    errorDescription: Option[String],
    createdAt: BusinessDateTime,
    updatedAt: BusinessDateTime,
    deletedAt: Option[BusinessDateTime],
    createdUser: AuditUser,
    updatedUser: AuditUser,
    deletedUser: AuditUser,
    deleted: Long,
    lockVersion: Long
)
