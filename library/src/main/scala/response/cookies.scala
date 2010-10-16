package unfiltered.response

import unfiltered.Cookie

case class ResponseCookies(cookies: Cookie*) extends unfiltered.response.Responder {
  def respond[T](res: HttpResponse[T]) = res.cookies(cookies)
}