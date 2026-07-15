package io.github.stoneream.dachshund.infra.db.generated

import scalikejdbc.WrappedResultSet

object ExternalAuthRequestTable {
  val Name = "external_auth_request"

  object Columns {
    val Id = "id"
    val FlowType = "flow_type"
    val ProviderType = "provider_type"
    val State = "state"
    val Nonce = "nonce"
    val CodeVerifier = "code_verifier"
    val RedirectUri = "redirect_uri"
    val Scopes = "scopes"
    val Status = "status"
    val ExpiresAt = "expires_at"
    val CompletedAt = "completed_at"
    val ErrorCode = "error_code"
    val ErrorDescription = "error_description"
    val CreatedAt = "created_at"
    val UpdatedAt = "updated_at"
    val DeletedAt = "deleted_at"
    val CreatedUser = "created_user"
    val UpdatedUser = "updated_user"
    val DeletedUser = "deleted_user"
    val Deleted = "deleted"
    val LockVersion = "lock_version"

    val All: Seq[String] = Seq(
      Id,
      FlowType,
      ProviderType,
      State,
      Nonce,
      CodeVerifier,
      RedirectUri,
      Scopes,
      Status,
      ExpiresAt,
      CompletedAt,
      ErrorCode,
      ErrorDescription,
      CreatedAt,
      UpdatedAt,
      DeletedAt,
      CreatedUser,
      UpdatedUser,
      DeletedUser,
      Deleted,
      LockVersion
    )
  }

  val InsertAuditColumnNames: Seq[String] = Seq(Columns.CreatedAt, Columns.CreatedUser)
  val UpdateAuditColumnNames: Seq[String] = Seq(Columns.UpdatedAt, Columns.UpdatedUser, Columns.LockVersion)
  val DeleteAuditColumnNames: Seq[String] = Seq(Columns.DeletedAt, Columns.DeletedUser, Columns.Deleted)

  def map(rs: WrappedResultSet): ExternalAuthRequestDbRow =
    ExternalAuthRequestDbRow(
      id = rs.long(Columns.Id),
      flowType = rs.string(Columns.FlowType),
      providerType = rs.string(Columns.ProviderType),
      state = rs.string(Columns.State),
      nonce = rs.string(Columns.Nonce),
      codeVerifier = rs.stringOpt(Columns.CodeVerifier),
      redirectUri = rs.string(Columns.RedirectUri),
      scopes = rs.string(Columns.Scopes),
      status = rs.string(Columns.Status),
      expiresAt = rs.localDateTime(Columns.ExpiresAt),
      completedAt = rs.localDateTimeOpt(Columns.CompletedAt),
      errorCode = rs.stringOpt(Columns.ErrorCode),
      errorDescription = rs.stringOpt(Columns.ErrorDescription),
      createdAt = rs.localDateTime(Columns.CreatedAt),
      updatedAt = rs.localDateTime(Columns.UpdatedAt),
      deletedAt = rs.localDateTimeOpt(Columns.DeletedAt),
      createdUser = rs.string(Columns.CreatedUser),
      updatedUser = rs.string(Columns.UpdatedUser),
      deletedUser = rs.string(Columns.DeletedUser),
      deleted = rs.long(Columns.Deleted),
      lockVersion = rs.long(Columns.LockVersion)
    )
}
