package unfiltered.response

import unfiltered.Cookie

case class ResponseCookies(cookies: Cookie*) extends Responder[Any] {
  def respond(res: HttpResponse[Any]) = res.cookies(cookies)
}
