package unfiltered.response

import javax.servlet.http.HttpServletResponse
import java.io.PrintWriter

trait ResponseWriter extends Responder {
  def respond(res: HttpServletResponse) {
    val writer = res.getWriter()
    try { write(writer) }
    finally { writer.close() }
  }
  def write(writer: PrintWriter): Unit
}
case class ResponseString(content: String) extends ResponseWriter {
  def write(writer: PrintWriter) { writer.write(content) }
}

case class Html(nodes: scala.xml.NodeSeq) extends ChainResponse(HtmlContent ~> ResponseString(nodes.toString))