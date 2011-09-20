package unfiltered.response

trait BaseContentType extends Responder[Any] {
  def respond(res: HttpResponse[Any]) {
    res.header("Content-Type", contentType(res))
  }
  def contentType(res: HttpResponse[Any]): String
}

case class CharContentType(contentType: String) extends BaseContentType {
  def contentType(res: HttpResponse[Any]) =
    "%s; charset=%s".format(contentType, res.charset.name.toLowerCase)
}

object CssContent extends CharContentType("text/css")
object HtmlContent extends CharContentType("text/html")
object JsContent extends CharContentType("text/javascript")
object CsvContent extends CharContentType("text/csv")
object TextXmlContent extends CharContentType("text/xml")
object PlainTextContent extends CharContentType("text/plain")
object JsonContent extends CharContentType("application/json")
object ApplicationXmlContent extends CharContentType("application/xml")
object FormEncodedContent extends ContentType("application/x-www-form-urlencoded")

case class ContentType(val staticContentType: String)
extends BaseContentType {
  def contentType(res: HttpResponse[Any]) = staticContentType
}
object PdfContent extends ContentType("application/pdf")
