package unfiltered.response

case class Redirect(loc: String) extends Responder {
  def respond[T](res: HttpResponse[T]) { res.sendRedirect(loc) }
}
