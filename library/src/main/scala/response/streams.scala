package unfiltered.response

import java.io.OutputStream
import java.util.zip.{GZIPOutputStream => GZOS}

object Stream {
  def closeAfter[S <: OutputStream](os: S)(op: S => Unit) {
    try { op(os) }
    finally { os.close() }
  }
}

trait ResponseStreamer extends Responder[Any] {
  def respond(res: HttpResponse[Any]) {
    Stream.closeAfter(res.getOutputStream())(stream)
  }
  def stream(os: OutputStream): Unit
}

case class ResponseBytes(content: Array[Byte]) extends ResponseStreamer {
  def stream(os: OutputStream) { os.write(content) }
}

/** Enclose the response's output stream in another stream,
 * typically a subclass of java.io.FilterOutputStream */
trait ResponseFilter[S <: OutputStream] extends ResponseFunction[Any] {
  def apply[T](res: HttpResponse[T]) = {
    new FilterStreamResponse(res)(filter)
  }
  def filter(os: OutputStream): S
}

object GZip extends ResponseFilter[GZOS] {
  def filter(os: OutputStream) = new GZOS(os)
}

/** Replaces the given response's output stream with one
 * provided by the given function, delegates the rest. */
class FilterStreamResponse[+T](val delegate: HttpResponse[T])
                              (f: OutputStream => OutputStream)
extends HttpResponse[T](delegate.underlying) {
  val getOutputStream = f(delegate.getOutputStream)
  def setContentType(contentType: String) {
    delegate.setContentType(contentType)
  }
  def setStatus(statusCode: Int) {
    delegate.setStatus(statusCode)
  }
  def sendRedirect(url: String) {
    delegate.sendRedirect(url)
  }
  def addHeader(name: String, value: String) {
    delegate.addHeader(name, value)
  }
  def cookies(cookie: Seq[unfiltered.Cookie]) {
    delegate.cookies(cookie)
  }
}
