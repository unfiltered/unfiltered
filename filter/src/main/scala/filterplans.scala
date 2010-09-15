package unfiltered.filter

import javax.servlet.{Filter, FilterConfig, FilterChain, ServletRequest, ServletResponse}
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import unfiltered.plan._
import unfiltered.request._
import unfiltered.response._

trait InittedFilter extends Filter {
  private var config_var: FilterConfig = _
  def init(config: FilterConfig) { config_var = config; }
  def config = config_var

  def destroy { }
}

/**
 * Servlet filter that wraps a plan and adheres to standard filter chain behaviour.
 */
class ServletFilterPlan(val plan: Plan) extends InittedFilter {
  val filter = plan.filter
  def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
    (request, response) match {
      case (hreq: HttpServletRequest, hres: HttpServletResponse) =>
        val request = new RequestBinding(hreq)
        val response = new ResponseBinding(hres)
        (try {
          filter(request)
        } catch {
          case m: MatchError =>
            Pass
        }) match {
          case after: PassAndThen =>
            chain.doFilter(request.underlying, response.underlying)
            after.then(request)(response)
          case Pass => chain.doFilter(request.underlying, response.underlying)
          case response_function => response_function(response)
        }
     }
  }
}
