package unfiltered.response

import java.io.PrintWriter

trait ResponseWriter extends Responder[Any] {
  def respond(res: HttpResponse[Any]) {
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
