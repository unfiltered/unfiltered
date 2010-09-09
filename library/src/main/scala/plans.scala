package unfiltered

import response._
import request._
import response.ResponsePackage.ResponseFunction



/** To ecapsulate a filter in a class definition */
trait Plan  {
  def filter: PartialFunction[HttpRequest, ResponseFunction]

  /*def doFilter(request: HttpRequest, response: HttpResponse, chain: FilterChain) {
    (request, response) match {
      case (request: HttpServletRequest, response: HttpHttpResponse) =>
        (try {
          filter(request)
        } catch {
          case m: MatchError =>
            Pass
        }) match {
          case after: PassAndThen =>
            chain.doFilter(request, response)
            after.then(request)(response)
          case Pass => chain.doFilter(request, response)
          case response_function => response_function(response)
        }
     }
  }*/
}

/** To define a filter class with an independent function */
class Planify(val filter: PartialFunction[HttpRequest, ResponseFunction]) extends Plan
/** To create a filter instance with an independent function */
object Planify {
  def apply(filter: PartialFunction[HttpRequest, ResponseFunction]) = new Planify(filter)
}
