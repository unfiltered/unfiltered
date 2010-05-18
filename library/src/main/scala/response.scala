package unfiltered.response

import java.io.PrintWriter
import javax.servlet.http.HttpServletResponse

trait Response
object Pass extends Response

trait Responder extends Response {
  def status = HttpServletResponse.SC_OK
  def respond(res: HttpServletResponse) {
    res.setStatus(status)
    header(res)
    body(res)
  }
  def header(res: HttpServletResponse): Unit
  def body(res: HttpServletResponse): Unit
}
trait ContentResponder extends Responder {
  def header(res: HttpServletResponse) {
    res.setContentType("%s; charset=%s".format(content_type, charset))
  }
  def body(res: HttpServletResponse) {
    val writer = res.getWriter()
    try { write(writer) }
    finally { writer.close() }
  }
  def content_type: String
  def charset: String = "utf-8"
  def write(writer: PrintWriter): Unit
}

abstract class StringResponder(content: String) extends ContentResponder {
  def write(writer: PrintWriter) {
    writer.write(content)
  }
}

trait CssContent extends ContentResponder {
  def content_type = "text/css"
}

trait HtmlContent extends ContentResponder {
  def content_type = "text/html"
}
case class Html(nodes: scala.xml.NodeSeq) extends StringResponder(nodes.toString) with HtmlContent