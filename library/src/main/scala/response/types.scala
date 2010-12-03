package unfiltered.response

trait BaseContentType extends Responder[Any] {
  def respond(res: HttpResponse[Any]) {
    res.setContentType(contentType)
  }
  def contentType: String
}

case class CharContentType(content_type: String) extends BaseContentType {
  def contentType = "%s; charset=%s".format(content_type, charset)
  def charset = "utf-8"
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

case class ContentType(override val contentType: String) extends BaseContentType
object PdfContent extends ContentType("application/pdf")
