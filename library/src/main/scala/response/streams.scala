package unfiltered.response

import javax.servlet.http.HttpServletResponse
import javax.servlet.ServletOutputStream

trait ResponseStreamer extends Responder {
  def respond(res: HttpServletResponse) {
    val os = res.getOutputStream()
    try { stream(os) }
    finally { os.close() }
  }
  def stream(os: ServletOutputStream): Unit
}
case class ResponseBytes(content: Array[Byte]) extends ResponseStreamer {
  def stream(os: ServletOutputStream) { os.write(content) }
}