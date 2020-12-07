package unfiltered.filter

import unfiltered.response._
import unfiltered.Async
import jakarta.servlet.http.HttpServletResponse

trait AsyncBinding extends Async.Responder[HttpServletResponse] {
  self: RequestBinding =>

  private[filter] val async: jakarta.servlet.AsyncContext
  private[filter] val filterChain: jakarta.servlet.FilterChain

  def respond(rf: ResponseFunction[HttpServletResponse]): Unit = {
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
