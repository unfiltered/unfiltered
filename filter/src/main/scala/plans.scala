package unfiltered.filter

import javax.servlet.{Filter, FilterConfig, FilterChain, ServletRequest, ServletResponse}
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import unfiltered.Unfiltered.Intent
import unfiltered.request._
import unfiltered.response._

trait InittedFilter extends Filter {
  private var config_var: FilterConfig = _
  def init(config: FilterConfig) { config_var = config; }
  def config = config_var

  def destroy { }
}

/**
 * Servlet filter that wraps an Intent and adheres to standard filter chain behaviour.
 */
trait Plan extends InittedFilter with unfiltered.PassingIntent[HttpServletRequest] {
  def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
    (request, response) match {
      case (hreq: HttpServletRequest, hres: HttpServletResponse) =>
        val request = new RequestBinding(hreq)
        val response = new ResponseBinding(hres)
        attempt(request) match {
          case after: PassAndThen =>
            chain.doFilter(request.underlying, response.underlying)
            after.then(request)(response)
          case Pass => chain.doFilter(request.underlying, response.underlying)
          case response_function => response_function(response)
        }
     }
  }
}

/** To define a filter class with an independent function */
class Planify(val intent: Intent[HttpServletRequest]) extends Plan
/** To create a filter instance with an independent function */
object Planify {
  def apply(intent: Intent[HttpServletRequest]) = new Planify(intent)
}
