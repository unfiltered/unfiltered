package unfiltered.response

import java.io.OutputStream
import java.util.zip.{GZIPOutputStream => GZOS}

object Stream {
  def apply[S <: OutputStream](os: S)(op: S => Unit) {
    try { op(os) }
    finally { os.close() }
  }
}

trait ResponseStreamer extends Responder[Any] {
  def respond(res: HttpResponse[Any]) {
    Stream(res.getOutputStream()) { stream }
  }
  def stream(os: OutputStream): Unit
}
case class ResponseBytes(content: Array[Byte]) extends ResponseStreamer {
  def stream(os: OutputStream) { os.write(content) }
}

trait FilteredStreamer[S <: OutputStream] extends Responder[Any] {
  def respond(res: HttpResponse[Any]) {
    Stream(filter(res.getOutputStream())) { stream }
  }
  def filter(os: OutputStream): S
  def stream(os: S): Unit
}
case class GZip(content: Array[Byte]) extends FilteredStreamer[GZOS] {
  def filter(os: OutputStream) = new GZOS(os)
  def stream(os: GZOS) {
    os.write(content)
  }
}
