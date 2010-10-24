package unfiltered.response

case class Redirect(loc: String) extends Responder {
  def respond(res: HttpResponse[Any]) { res.sendRedirect(loc) }
}
