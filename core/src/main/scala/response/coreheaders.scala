package unfiltered.response

case class ResponseHeader(name: String, values: Iterable[String]) extends Responder[Any] {
  def respond(res: HttpResponse[Any]) {
    values.foreach { v => res.header(name, v) } 
  }
}

class HeaderName(val name: String) {
  def apply(value: String*) = ResponseHeader(name, value)
}

object Connection extends HeaderName("Connection")
object ContentLength extends HeaderName("Content-Length")
