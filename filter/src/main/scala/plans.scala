package unfiltered.filter

import javax.servlet.{Filter, FilterConfig, FilterChain, ServletRequest, ServletResponse}
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import unfiltered.Unfiltered.Intent
import unfiltered.request._
import unfiltered.response._

trait InittedFilter extends Filter {
  private var configVar: FilterConfig = _
  def init(config: FilterConfig) { configVar = config; }
  def config = configVar

  def destroy { }
}

/**
 * Servlet filter that wraps an Intent and adheres to standard filter chain behaviour.
 */
trait Plan extends InittedFilter {
  def complete(intent: Intent[HttpServletRequest, ResponseFunction]) =
    intent.orElse({ case _ => Pass }: Intent[HttpServletRequest, ResponseFunction])
  def intent: Intent[HttpServletRequest, ResponseFunction]
      
  def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
    (request, response) match {
      case (hreq: HttpServletRequest, hres: HttpServletResponse) =>
        val request = new RequestBinding(hreq)
        val response = new ResponseBinding(hres)
        complete(intent)(request) match {
          case after: PassAndThen =>
            val hrw = PassAndThenResponseWrapper(response.underlying)
            chain.doFilter(request.underlying, hrw)
            after.then(request)(response) 
            response.getWriter.write(hrw.toString)
            response.getWriter.close
          case Pass => chain.doFilter(request.underlying, response.underlying)
          case responseFunction => responseFunction(response)
        }
     }
  }
}

/** To define a filter class with an independent function */
class Planify(val intent: Intent[HttpServletRequest, ResponseFunction]) extends Plan
/** To create a filter instance with an independent function */
object Planify {
  def apply(intent: Intent[HttpServletRequest, ResponseFunction]) = new Planify(intent)
}
