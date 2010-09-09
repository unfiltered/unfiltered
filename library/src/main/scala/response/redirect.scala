package unfiltered.response

case class Redirect(loc: String) extends Responder {
  def respond(res: HttpServletResponse) { res.sendRedirect(loc) }
}
