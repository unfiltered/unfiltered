package unfiltered.filter

import unfiltered.response._
import unfiltered.Async
import javax.servlet.http.HttpServletResponse

trait AsyncBinding extends Async.Responder[HttpServletResponse] {
  self: RequestBinding =>

  private[filter] val res: HttpServletResponse
  private[filter] val con: org.eclipse.jetty.continuation.Continuation
  private[filter] val filterChain: javax.servlet.FilterChain

  def respond(rf: ResponseFunction[HttpServletResponse]) {
    rf match {
      case Pass =>
        filterChain.doFilter(self.underlying, res)
      case rf =>
        rf(new ResponseBinding(res))
    }
    con.complete()
  }

}
