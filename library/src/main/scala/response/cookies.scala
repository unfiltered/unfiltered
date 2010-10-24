package unfiltered.response

import unfiltered.Cookie

case class ResponseCookies(cookies: Cookie*) extends unfiltered.response.Responder {
  def respond(res: HttpResponse[Any]) = res.cookies(cookies)
}
