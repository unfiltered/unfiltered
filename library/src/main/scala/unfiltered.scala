package unfiltered

import javax.servlet.{Filter, FilterConfig, FilterChain, ServletRequest, ServletResponse}
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import unfiltered.response._

object Unfiltered {
  type Handler = HttpServletResponse => HttpServletResponse
}
import Unfiltered.Handler

trait InittedFilter extends Filter {
  private var config_var: FilterConfig = _
  def init(config: FilterConfig) { config_var = config; }
  def config = config_var

  def destroy { }
}

class Plan(val filter: PartialFunction[ServletRequest, Handler]) extends InittedFilter {
  val complete_filter = filter.orElse[ServletRequest, Handler] {
    case _ => Pass
  }
  def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
    (request, response) match {
      case (request: HttpServletRequest, response: HttpServletResponse) => 
        complete_filter(request) match {
          case Pass => chain.doFilter(request, response)
          case r: Responder => r.respond(response)
        }
    }
  }
}
