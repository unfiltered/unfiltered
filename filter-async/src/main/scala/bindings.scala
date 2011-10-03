package unfiltered.filter

import unfiltered.Async
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import unfiltered.filter

trait AsyncBinding extends Async.Responder[HttpServletResponse]{

  private[filter] val con: org.eclipse.jetty.continuation.Continuation

  def respond(rf: unfiltered.response.ResponseFunction[HttpServletResponse]) {
    rf(new ResponseBinding(con.getServletResponse.asInstanceOf[HttpServletResponse]))
    con.complete   
  }

}
