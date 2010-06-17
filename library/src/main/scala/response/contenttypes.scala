package unfiltered.response

import javax.servlet.http.HttpServletResponse

case class ContentType(content_type: String) extends Responder {
  def respond(res: HttpServletResponse) {
    res.setContentType("%s; charset=%s".format(content_type, charset))
  }
  def charset = "utf-8"
}
object CssContent extends ContentType("text/css")
object HtmlContent extends ContentType("text/html")
