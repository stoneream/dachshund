package io.github.stoneream.dachshund.infra.db.generated

import scalikejdbc.WrappedResultSet

object UserSessionTokenTable {
  val Name = "user_session_token"

  object Columns {
    val Id = "id"
    val UserId = "user_id"
    val HashedToken = "hashed_token"
    val IssuedAt = "issued_at"
    val LastAccessedAt = "last_accessed_at"
    val IdleExpiresAt = "idle_expires_at"
    val ExpiresAt = "expires_at"
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
      UserId,
      HashedToken,
      IssuedAt,
      LastAccessedAt,
      IdleExpiresAt,
      ExpiresAt,
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

  def map(rs: WrappedResultSet): UserSessionTokenDbRow =
    UserSessionTokenDbRow(
      id = rs.long(Columns.Id),
      userId = rs.long(Columns.UserId),
      hashedToken = rs.string(Columns.HashedToken),
      issuedAt = rs.localDateTime(Columns.IssuedAt),
      lastAccessedAt = rs.localDateTime(Columns.LastAccessedAt),
      idleExpiresAt = rs.localDateTime(Columns.IdleExpiresAt),
      expiresAt = rs.localDateTime(Columns.ExpiresAt),
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
