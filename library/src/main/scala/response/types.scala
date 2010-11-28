package unfiltered.response

trait BaseContentType extends Responder[Any] {
  def respond(res: HttpResponse[Any]) {
    res.setContentType(contentType)
  }
  def contentType: String
}

case class ContentType(content_type: String) extends BaseContentType {
  def contentType = "%s; charset=%s".format(content_type, charset)
  def charset = "utf-8"
}
object CssContent extends ContentType("text/css")
object HtmlContent extends ContentType("text/html")
object JsContent extends ContentType("text/javascript")
object CsvContent extends ContentType("text/csv")
object TextXmlContent extends ContentType("text/xml")
object PlainTextContent extends ContentType("text/plain")
object JsonContent extends ContentType("application/json")
object ApplicationXmlContent extends ContentType("application/xml")

case class BinaryContentType(override val contentType: String) extends BaseContentType
object PdfContent extends BinaryContentType("application/pdf")
