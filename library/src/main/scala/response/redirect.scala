package unfiltered.response

import javax.servlet.http.HttpServletResponse

case class Redirect(loc: String) extends Responder {
  def respond(res: HttpServletResponse) { res.sendRedirect(loc) }
}
