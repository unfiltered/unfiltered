package unfiltered.filter.async

import unfiltered.filter.{AsyncBinding,RequestBinding,ResponseBinding}
import unfiltered.response.Pass
import unfiltered.request.HttpRequest
import jakarta.servlet.{FilterChain, ServletRequest, ServletResponse}
import jakarta.servlet.http.{HttpServletRequest, HttpServletResponse}
import unfiltered.Async
import Planify._

object Plan {
 
  type Intent =
    Async.Intent[HttpServletRequest,HttpServletResponse]
}

/** Object to facilitate Plan.Intent definitions. Type annotations
 *  are another option. */
object Intent {
  def apply(intent: Plan.Intent) = intent
}

/**
* provides an asynchronous Plan that's using jetty continuation under the hood
* the implementation follows the so called "Suspend Continue Pattern" where
* an asynchronous handler is used to generate the response
*/
trait Plan extends unfiltered.filter.InittedFilter {
  
  def intent: Plan.Intent

  def asyncRequestTimeoutMillis: Long = DEFAULT_ASYNC_REQUEST_TIMEOUT_MILLIS

  def doFilter(request: ServletRequest,
                        response: ServletResponse,
                        chain: FilterChain): Unit = {
    val asyncContext = request.startAsync
    asyncContext.setTimeout(asyncRequestTimeoutMillis)

     (request, response) match {
       case (hreq: HttpServletRequest, hres: HttpServletResponse) =>
          val requestBinding = new RequestBinding(hreq) with AsyncBinding {
            val async = asyncContext
            val filterChain = chain
          }
          val responseBinding = new ResponseBinding(hres)
          Pass.onPass(
            intent,
            (_: HttpRequest[HttpServletRequest]) =>
              chain.doFilter(requestBinding.underlying,
                             responseBinding.underlying)
          )(requestBinding)
      }
 }
}

object Planify {
  val DEFAULT_ASYNC_REQUEST_TIMEOUT_MILLIS = 30000L
  def apply(intentIn: Plan.Intent) = new Plan {
    def intent = intentIn
    override val asyncRequestTimeoutMillis = DEFAULT_ASYNC_REQUEST_TIMEOUT_MILLIS
  }
  def apply(intentIn: Plan.Intent, asyncRequestTimeoutMillisIn: Long) = 
    new Plan {
      val intent = intentIn

      override val asyncRequestTimeoutMillis = asyncRequestTimeoutMillisIn
    }
}
