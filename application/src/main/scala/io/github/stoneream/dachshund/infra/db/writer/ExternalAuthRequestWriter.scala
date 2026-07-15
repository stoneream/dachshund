package io.github.stoneream.dachshund.infra.db.writer

import io.github.stoneream.dachshund.infra.db.AuditUser
import io.github.stoneream.dachshund.infra.db.generated.ExternalAuthRequestDbRow
import io.github.stoneream.dachshund.lib.datetime.BusinessDateTime
import io.github.stoneream.dachshund.model.ExternalAuthRequestStatus
import scalikejdbc.*

import com.google.inject.{Inject, Singleton}

@Singleton
class ExternalAuthRequestWriter @Inject() () {
  def write(row: ExternalAuthRequestDbRow)(using DBSession): Long = {
    sql"""
      insert into external_auth_request (
        flow_type,
        provider_type,
        state,
        nonce,
        code_verifier,
        redirect_uri,
        scopes,
        status,
        expires_at,
        completed_at,
        error_code,
        error_description,
        created_at,
        updated_at,
        deleted_at,
        created_user,
        updated_user,
        deleted_user,
        deleted,
        lock_version
      ) values (
        {flowType},
        {providerType},
        {state},
        {nonce},
        {codeVerifier},
        {redirectUri},
        {scopes},
        {status},
        {expiresAt},
        {completedAt},
        {errorCode},
        {errorDescription},
        {createdAt},
        {updatedAt},
        {deletedAt},
        {createdUser},
        {updatedUser},
        {deletedUser},
        {deleted},
        {lockVersion}
      )
    """
      .bindByName(
        "flowType" -> row.flowType,
        "providerType" -> row.providerType,
        "state" -> row.state,
        "nonce" -> row.nonce,
        "codeVerifier" -> row.codeVerifier,
        "redirectUri" -> row.redirectUri,
        "scopes" -> row.scopes,
        "status" -> row.status,
        "expiresAt" -> row.expiresAt,
        "completedAt" -> row.completedAt,
        "errorCode" -> row.errorCode,
        "errorDescription" -> row.errorDescription,
        "createdAt" -> row.createdAt,
        "updatedAt" -> row.updatedAt,
        "deletedAt" -> row.deletedAt,
        "createdUser" -> row.createdUser,
        "updatedUser" -> row.updatedUser,
        "deletedUser" -> row.deletedUser,
        "deleted" -> row.deleted,
        "lockVersion" -> row.lockVersion
      )
      .updateAndReturnGeneratedKey
      .apply()
  }

  def updateStatus(
      id: Long,
      status: ExternalAuthRequestStatus,
      expectedStatus: ExternalAuthRequestStatus,
      completedAt: Option[BusinessDateTime],
      errorCode: Option[String],
      errorDescription: Option[String],
      requireUnexpired: Boolean,
      checkedAt: BusinessDateTime,
      updatedAt: BusinessDateTime,
      updatedUser: AuditUser
  )(using DBSession): Boolean = {
    val requireUnexpiredFlag = if (requireUnexpired) 1 else 0

    sql"""
      update external_auth_request
      set
        status = {processStatus},
        completed_at = {completedAt},
        error_code = {errorCode},
        error_description = {errorDescription},
        updated_at = {updatedAt},
        updated_user = {updatedUser},
        lock_version = lock_version + 1
      where
        id = {id}
        and status = {pendingStatus}
        and ({requireUnexpired} = 0 or expires_at >= {now})
        and deleted = 0
    """
      .bindByName(
        "id" -> id,
        "processStatus" -> status.dbValue,
        "pendingStatus" -> expectedStatus.dbValue,
        "completedAt" -> completedAt.map(_.toLocalDateTime),
        "errorCode" -> errorCode,
        "errorDescription" -> errorDescription,
        "requireUnexpired" -> requireUnexpiredFlag,
        "now" -> checkedAt.toLocalDateTime,
        "updatedAt" -> updatedAt.toLocalDateTime,
        "updatedUser" -> updatedUser.dbValue
      )
      .update
      .apply() == 1
  }
}
