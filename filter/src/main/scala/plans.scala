package unfiltered.filter

import javax.servlet.{Filter, FilterConfig, FilterChain, ServletRequest, ServletResponse}
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import unfiltered.request._
import unfiltered.response._
import unfiltered.Cycle

trait InittedFilter extends Filter {
  private var configVar: FilterConfig = _
  override def init(config: FilterConfig): Unit = { configVar = config; }
  def config = configVar

  override def destroy(): Unit = { }
}

object Plan {
  type Intent = Cycle.Intent[HttpServletRequest,HttpServletResponse]
}

/** Object to facilitate Plan.Intent definitions. Type annotations
 *  are another option. */
object Intent {
  def apply(intent: Plan.Intent) = intent
}

/**
 * Servlet filter that wraps an Intent and adheres to standard filter
 * chain behaviour.
 */
trait Plan extends InittedFilter {
  def intent: Plan.Intent
  def doFilter(request: ServletRequest,
               response: ServletResponse,
               chain: FilterChain): Unit = {
    (request, response) match {
      case (hreq: HttpServletRequest, hres: HttpServletResponse) =>
        val request = new RequestBinding(hreq)
        val response = new ResponseBinding(hres)
        Pass.fold(
          intent,
          (_: HttpRequest[HttpServletRequest]) =>
            chain.doFilter(request.underlying, response.underlying),
          (_: HttpRequest[HttpServletRequest], 
           rf: ResponseFunction[HttpServletResponse]) => {
            val res = rf(response)
            res.outputStream.close()
          }
        )(request)
     }
  }
}

class Planify(val intent: Plan.Intent) extends Plan
/** To create a filter instance with an independent function */
object Planify {
  def apply(intentIn: Plan.Intent) = new Plan {
    val intent = intentIn
  }
}
