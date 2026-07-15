package io.github.stoneream.dachshund.infra.db.reader.auth.callback

import io.github.stoneream.dachshund.model.{ExternalAuthFlowType, ExternalAuthProviderType, ExternalAuthRequest, ExternalAuthRequestStatus}
import scalikejdbc.*

import com.google.inject.{Inject, Singleton}

@Singleton
class ExternalAuthRequestReader @Inject() () {
  def findByState(state: String)(using DBSession): Option[ExternalAuthRequest] =
    sql"""
      select
        id,
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
        error_description
      from external_auth_request
      where
        state = {state}
        and deleted = 0
      limit 1
    """
      .bindByName("state" -> state)
      .map { rs =>
        ExternalAuthRequest(
          id = rs.long("id"),
          flowType = ExternalAuthFlowType.fromDbValue(rs.string("flow_type")),
          providerType = ExternalAuthProviderType.fromDbValue(rs.string("provider_type")),
          state = rs.string("state"),
          nonce = rs.string("nonce"),
          codeVerifier = rs.stringOpt("code_verifier"),
          redirectUri = rs.string("redirect_uri"),
          scopes = rs.string("scopes"),
          status = ExternalAuthRequestStatus.fromDbValue(rs.string("status")),
          expiresAt = rs.localDateTime("expires_at"),
          completedAt = rs.localDateTimeOpt("completed_at"),
          errorCode = rs.stringOpt("error_code"),
          errorDescription = rs.stringOpt("error_description")
        )
      }
      .single
      .apply()
}
