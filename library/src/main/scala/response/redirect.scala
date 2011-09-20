package unfiltered.response

case class Redirect(loc: String) extends Responder[Any] {
  def respond(res: HttpResponse[Any]) { res.redirect(loc) }
}
