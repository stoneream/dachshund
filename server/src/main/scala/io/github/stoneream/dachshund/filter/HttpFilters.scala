package io.github.stoneream.dachshund.filter

import play.api.http.HttpFilters as PlayHttpFilters
import play.api.mvc.EssentialFilter
import play.filters.cors.CORSFilter
import play.filters.csp.CSPFilter
import play.filters.csrf.CSRFFilter
import play.filters.headers.SecurityHeadersFilter
import play.filters.hosts.AllowedHostsFilter

import com.google.inject.{Inject, Singleton}

@Singleton
class HttpFilters @Inject() (
    customLoggingFilter: CustomLoggingFilter,
    allowedHostsFilter: AllowedHostsFilter,
    csrfFilter: CSRFFilter,
    corsFilter: CORSFilter,
    securityHeadersFilter: SecurityHeadersFilter,
    cspFilter: CSPFilter
) extends PlayHttpFilters {
  override def filters: Seq[EssentialFilter] = Seq(
    customLoggingFilter,
    allowedHostsFilter,
    csrfFilter,
    corsFilter,
    securityHeadersFilter,
    cspFilter
  )
}
