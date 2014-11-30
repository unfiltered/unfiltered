package unfiltered.filter

import unfiltered.response._
import unfiltered.Async
import javax.servlet.http.HttpServletResponse

trait AsyncBinding extends Async.Responder[HttpServletResponse] {
  self: RequestBinding =>

  private[filter] val async: javax.servlet.AsyncContext
  private[filter] val filterChain: javax.servlet.FilterChain

  def respond(rf: ResponseFunction[HttpServletResponse]) {
    rf match {
      case Pass =>
        filterChain.doFilter(self.underlying, async.getResponse)
      case rf =>
        rf(new ResponseBinding(
          async.getResponse.asInstanceOf[HttpServletResponse]
        ))
    }
    async.complete
  }

}
