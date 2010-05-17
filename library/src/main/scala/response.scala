package unfiltered.response

import java.io.PrintWriter
import javax.servlet.http.HttpServletResponse

trait Response
object Pass extends Response

trait Responder extends Response {
  def respond(res: HttpServletResponse): Unit
}
trait Writer extends Responder {
  def respond(res: HttpServletResponse) {
    res.setContentType("%s; charset=%s".format(content_type, charset))
    val writer = res.getWriter()
    try { write(writer) } 
    finally { writer.close() }
  }
  def content_type: String
  def charset: String = "utf-8"
  def write(writer: PrintWriter): Unit
}

case class Html(nodes: scala.xml.NodeSeq) extends Writer {
  def write(writer: PrintWriter) {
    writer.write(nodes.toString)
  }
  def content_type = "text/html"
}