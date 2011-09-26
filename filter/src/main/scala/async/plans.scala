package unfiltered.filter.async

import org.eclipse.jetty.continuation.ContinuationSupport
import org.eclipse.jetty.continuation.Continuation
import util._
import unfiltered.filter.{RequestBinding,ResponseBinding}
import unfiltered.response.{NotFound, Pass}
import unfiltered.request.HttpRequest
import javax.servlet.{Filter, FilterConfig, FilterChain, ServletRequest, ServletResponse}
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import unfiltered.Async

object Plan {
  /** Note: The only return object a channel plan acts on is Pass */
  type Intent =
    Async.Intent[HttpServletRequest,HttpServletResponse]
}
/** Object to facilitate Plan.Intent definitions. Type annotations
 *  are another option. */
object Intent {
  def apply(intent: Plan.Intent) = intent
}

trait Plan extends unfiltered.filter.InittedFilter {
  
  def intent: Plan.Intent

  def doFilter(request: ServletRequest,
                        response: ServletResponse,
                        chain: FilterChain) {
   val continuation = ContinuationSupport.getContinuation(request)
   if (continuation.isExpired) {
       response.asInstanceOf[HttpServletResponse].setStatus(408)
       chain.doFilter(request,response)
   } else {
     continuation.suspend()
          (request, response) match {
            case (hreq: HttpServletRequest, hres: HttpServletResponse) =>
              val requestBinding = new RequestBinding(hreq, continuation)
              val responseBinding = new ResponseBinding(hres)
              intent.orElse({ case _ => Pass }: Plan.Intent)(requestBinding) match {
                case Pass =>
                  chain.doFilter(requestBinding.underlying, responseBinding.underlying)
                case _ => ()
              }   
          }  
   }
 }
}

class Planify(val intent: Plan.Intent) extends Plan

object Planify {
  def apply(intent: Plan.Intent) = new Planify(intent)
}
