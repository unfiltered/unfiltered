package unfiltered.response

import java.io.{Writer,OutputStreamWriter}

trait ResponseWriter extends Responder[Any] {
  def respond(res: HttpResponse[Any]) {
    val writer = new OutputStreamWriter(res.outputStream, res.charset)
    try { write(writer) }
    finally { writer.close() }
  }
  def write(writer: Writer): Unit
}
case class ResponseString(content: String) extends ResponseWriter {
  def write(writer: Writer) { writer.write(content) }
}

case class Html(nodes: scala.xml.NodeSeq) extends 
ComposeResponse(HtmlContent ~> ResponseString(nodes.toString))

case class Charset(charset: java.nio.charset.Charset)
extends ResponseFunction[Any] {
  def apply[T](delegate: HttpResponse[T]) =
    new DelegatingResponse(delegate) {
      override val charset = Charset.this.charset
    }
}
