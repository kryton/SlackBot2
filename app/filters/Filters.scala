package filters

/**
  * Created by iholsman on 9/28/2016.
  */
import javax.inject.Inject

import play.api.http.HttpFilters
import play.api.mvc.EssentialFilter
import play.filters.csrf.CSRFFilter
import play.filters.gzip.GzipFilter
import play.filters.headers.SecurityHeadersFilter

class Filters @Inject()(csrfFilter: CSRFFilter,
                        gzipFilter: GzipFilter,
                        securityHeadersFilter: SecurityHeadersFilter)
  extends  HttpFilters {
  override def filters: Seq[EssentialFilter] = Seq (csrfFilter, securityHeadersFilter, gzipFilter)
}
