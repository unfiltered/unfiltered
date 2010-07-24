package unfiltered

import javax.servlet.{Filter, FilterConfig, FilterChain, ServletRequest, ServletResponse}
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import unfiltered.response.{ResponsePackage, Pass, PassAndThen}
import ResponsePackage.ResponseFunction

trait InittedFilter extends Filter {
  private var config_var: FilterConfig = _
  def init(config: FilterConfig) { config_var = config; }
  def config = config_var

  def destroy { }
}

/** To ecapsulate a filter in a class definition */
trait Plan extends InittedFilter {
  def filter: PartialFunction[ServletRequest, ResponseFunction]
  val complete_filter = filter.orElse[ServletRequest, ResponseFunction] {
    case _ => Pass
  }
  def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
    (request, response) match {
      case (request: HttpServletRequest, response: HttpServletResponse) => 
        complete_filter(request) match {
          case after: PassAndThen[_] =>
            chain.doFilter(request, response)
            after.then(request, response)
          case Pass => chain.doFilter(request, response)
          case response_function => response_function(response)
        }
    }
  }
}

/** To define a filter class with an independent function */
class Planify(val filter: PartialFunction[ServletRequest, ResponseFunction]) extends Plan
/** To create a filter instance with an independent function */
object Planify {
  def apply(filter: PartialFunction[ServletRequest, ResponseFunction]) = new Planify(filter)
}
