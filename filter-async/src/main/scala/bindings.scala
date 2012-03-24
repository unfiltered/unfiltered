package unfiltered.filter

import unfiltered.response._
import unfiltered.Async
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import unfiltered.filter

trait AsyncBinding extends Async.Responder[HttpServletResponse] {
  self: RequestBinding =>

  private[filter] val con: org.eclipse.jetty.continuation.Continuation
  private[filter] val filterChain: javax.servlet.FilterChain

  def respond(rf: ResponseFunction[HttpServletResponse]) {
    rf match {
      case Pass =>
        filterChain.doFilter(self.underlying, con.getServletResponse)
      case rf =>
        rf(new ResponseBinding(
          con.getServletResponse.asInstanceOf[HttpServletResponse]
        ))
    }
    con.complete
  }

}
