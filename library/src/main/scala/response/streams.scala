package unfiltered.response

import java.io.OutputStream

object Stream {
  def closeAfter[S <: OutputStream](os: S)(op: S => Unit): Unit = {
    try { op(os) }
    finally { os.close() }
  }
}

trait ResponseStreamer extends Responder[Any] {
  def respond(res: HttpResponse[Any]): Unit = {
    Stream.closeAfter(res.outputStream)(stream)
  }
  def stream(os: OutputStream): Unit
}

case class ResponseBytes(content: Array[Byte]) extends ResponseStreamer {
  def stream(os: OutputStream): Unit = { os.write(content) }
}
