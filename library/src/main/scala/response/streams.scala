package unfiltered.response

import java.io.OutputStream

trait ResponseStreamer extends Responder[Any] {
  def respond(res: HttpResponse[Any]) {
    val os = res.getOutputStream()
    try { stream(os) }
    finally { os.close() }
  }
  def stream(os: OutputStream): Unit
}
case class ResponseBytes(content: Array[Byte]) extends ResponseStreamer {
  def stream(os: OutputStream) { os.write(content) }
}
